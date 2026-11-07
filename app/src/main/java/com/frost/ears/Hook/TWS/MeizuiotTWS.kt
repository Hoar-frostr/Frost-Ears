package com.frost.ears.hook.TWS

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import com.frost.ears.utils.XLog

object MeizuiotTWS {

    private const val TAG = "MzfpHook"

    // 与蓝牙进程（BluetoothHook）约定的广播动作（品牌无关，见 TwsActions）
    private const val ACTION_BATTERY = TwsActions.ACTION_BATTERY
    private const val ACTION_DEVICE_INFO = TwsActions.ACTION_DEVICE_INFO

    @Volatile private var vivoLeft = -1
    @Volatile private var vivoRight = -1
    @Volatile private var vivoBox = -1

    @Volatile private var currentVivoMac: String? = null
    @Volatile private var currentVivoName: String? = null
    @Volatile private var currentVivoModel: Int = -1

    fun hook(module: XposedModule, param: PackageReadyParam) {
        if (!module.getRemotePreferences("frost_tool_prefs").getBoolean("tws_device_spoof_enabled", false)) return
        registerBatteryReceiver(module, param)
        hookScanFilter(module, param)
        hookRssi(module, param)
        hookFakeMzfpDevice(module, param)
        hookModeIdWhiteList(module, param)
    }

    // ========================================================================
    // ========== 注册广播接收器 ==========
    // ========================================================================
    private fun registerBatteryReceiver(module: XposedModule, param: PackageReadyParam) {
        try {
            val serviceClass = param.classLoader.loadClass(
                "com.meizu.meizuiot.bluetooth.mzfp.MzfpService"
            )
            val onCreate = serviceClass.getDeclaredMethod("onCreate")

            module.hook(onCreate).intercept { chain ->
                chain.proceed()

                val service = chain.thisObject as? Context
                if (service != null) {
                    // 电量广播
                    try {
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context?, intent: Intent?) {
                                val left = intent?.getIntExtra("left", -1) ?: -1
                                val right = intent?.getIntExtra("right", -1) ?: -1
                                val box = intent?.getIntExtra("box", -1) ?: -1

                                if (left >= 0) vivoLeft = left
                                if (right >= 0) vivoRight = right
                                if (box >= 0) vivoBox = box

                                updateDbBattery(service, module)
                            }
                        }
                        val filter = IntentFilter(ACTION_BATTERY)
                        if (Build.VERSION.SDK_INT >= 33) {
                            service.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                        } else {
                            service.registerReceiver(receiver, filter)
                        }
                    } catch (e: Throwable) {}

                    // 设备信息广播
                    try {
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context?, intent: Intent?) {
                                val name = intent?.getStringExtra("name")
                                val mac = intent?.getStringExtra("mac")
                                val model = intent?.getIntExtra("model", -1) ?: -1

                                if (mac != null && mac.isNotEmpty()) {
                                    currentVivoMac = mac
                                }
                                if (!name.isNullOrEmpty()) {
                                    currentVivoName = name
                                }
                                if (model >= 0) {
                                    currentVivoModel = model
                                }
                            }
                        }
                        val filter = IntentFilter(ACTION_DEVICE_INFO)
                        if (Build.VERSION.SDK_INT >= 33) {
                            service.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                        } else {
                            service.registerReceiver(receiver, filter)
                        }
                    } catch (e: Throwable) {}
                }
                null
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "registerBatteryReceiver failed", e)
        }
    }

    // ========================================================================
    // ========== 更新数据库里的电量 ==========
    // ========================================================================
    private fun updateDbBattery(context: Context, module: XposedModule) {
        try {
            val mac = currentVivoMac ?: return

            val values = ContentValues()
            if (vivoLeft >= 0) values.put("left_bat", vivoLeft)
            if (vivoRight >= 0) values.put("right_bat", vivoRight)
            if (vivoBox >= 0) values.put("box_bat", vivoBox)

            if (values.size() == 0) return

            val uri = Uri.parse("content://com.meizu.meizuiot.provider.MzfpDevice/devices")
            context.contentResolver.update(uri, values, "address=?", arrayOf(mac))
        } catch (e: Throwable) {}
    }

    // ========================================================================
    // ========== ① 清空 ScanFilter ==========
    // ========================================================================
    private fun hookScanFilter(module: XposedModule, param: PackageReadyParam) {
        try {
            val scannerClass = param.classLoader.loadClass("android.bluetooth.le.BluetoothLeScanner")
            val startScanMethod = scannerClass.getDeclaredMethod(
                "startScan",
                java.util.List::class.java,
                android.bluetooth.le.ScanSettings::class.java,
                android.bluetooth.le.ScanCallback::class.java
            )
            module.hook(startScanMethod).intercept { chain ->
                val empty = java.util.ArrayList<Any>()
                val newArgs = chain.args.toMutableList()
                newArgs[0] = empty
                chain.proceed(newArgs.toTypedArray())
                null
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookScanFilter failed", e)
        }
    }

    // ========================================================================
    // ========== ② 放宽 RSSI ==========
    // ========================================================================
    private fun hookRssi(module: XposedModule, param: PackageReadyParam) {
        try {
            val scanResultClass = param.classLoader.loadClass("android.bluetooth.le.ScanResult")
            val getRssiMethod = scanResultClass.getDeclaredMethod("getRssi")
            module.hook(getRssiMethod).intercept { chain ->
                -40
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookRssi failed", e)
        }
    }

    // ========================================================================
    // ========== ③ 伪造 MzfpDevice（用 name 匹配） ==========
    // ========================================================================
    private fun hookFakeMzfpDevice(module: XposedModule, param: PackageReadyParam) {
        try {
            val gClass = param.classLoader.loadClass("i4.g")
            val bMethod = gClass.getDeclaredMethod(
                "b",
                param.classLoader.loadClass("android.bluetooth.le.ScanResult")
            )
            val mzfpDeviceClass = param.classLoader.loadClass(
                "com.meizu.meizuiot.bluetooth.mzfp.MzfpDevice"
            )

            module.hook(bMethod).intercept { chain ->
                val result = chain.args[0] as? ScanResult
                val orig = chain.proceed() as? Any
                if (orig != null) return@intercept orig

                if (result == null) return@intercept null
                val device = result.device ?: return@intercept null
                val name = device.name ?: return@intercept null
                val vivoName = currentVivoName ?: return@intercept null
                if (!name.contains(vivoName)) return@intercept null

                val left = vivoLeft
                val right = vivoRight
                val box = vivoBox

                try {
                    val fake = mzfpDeviceClass.getConstructor(
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        String::class.java,
                        String::class.java,
                        String::class.java,
                        BluetoothDevice::class.java
                    ).newInstance(
                        16, 0x20, 0, 1,
                        if (box >= 0) box else 0,
                        if (left >= 0) left else 0,
                        if (right >= 0) right else 0,
                        "e6878a",
                        "fake_" + device.address.replace(":", ""),
                        "",
                        device
                    )
                    fake
                } catch (e: Throwable) {
                    XLog.e(module, TAG, "new MzfpDevice failed", e)
                    null
                }
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookFakeMzfpDevice failed", e)
        }
    }

    // ========================================================================
    // ========== ④ 放行型号白名单 ==========
    // ========================================================================
    private fun hookModeIdWhiteList(module: XposedModule, param: PackageReadyParam) {
        try {
            val wClass = param.classLoader.loadClass("i4.w")
            val aMethod = wClass.getDeclaredMethod("a", String::class.java)
            module.hook(aMethod).intercept { chain ->
                true
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookModeIdWhiteList failed", e)
        }
    }
}
