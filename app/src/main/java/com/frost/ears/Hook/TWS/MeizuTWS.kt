package com.frost.ears.hook.TWS

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import com.frost.ears.utils.XLog

/**
 * 魅族耳机 app（com.meizu.earphone）hook。
 * 消费蓝牙进程 [BluetoothHook] 的设备信息/电量广播，把 vivo 耳机包装成魅族“官方”设备显示，
 * 并拦截降噪切换转发回蓝牙进程走 GAIA。佩戴检测强制已佩戴（控制中心切模式需要）。
 */
object MeizuTWS {

    private const val TAG = "MzfpHook"

    // 与蓝牙进程（BluetoothHook）约定的广播动作（品牌无关，见 TwsActions）
    private const val ACTION_BATTERY = TwsActions.ACTION_BATTERY
    private const val ACTION_SET_ANC = TwsActions.ACTION_SET_ANC
    private const val ACTION_DEVICE_INFO = TwsActions.ACTION_DEVICE_INFO

    @Volatile private var earbudLeft = -1
    @Volatile private var earbudRight = -1
    @Volatile private var earbudBox = -1

    // 1=关闭，2=降噪，3=通透
    @Volatile private var currentAncMode = 1

    @Volatile private var currentMac: String? = null
    @Volatile private var currentName: String? = null

    fun hook(module: XposedModule, param: PackageReadyParam) {
        if (!module.getRemotePreferences("frost_tool_prefs").getBoolean("tws_device_spoof_enabled", false)) return
        hookTwsControlService(module, param)
        hookQueryBattery(module, param)
        hookElectricityGetters(module, param)
        hookDeviceList(module, param)
        hookRemotePrefService(module, param)
        hookSetAncSwitch(module, param)
        hookWearingStatus(module, param)
        hookConnectionState(module, param)
    }

    // ========================================================================
    // ========== 连接状态：让 fake_ 设备被判定为“已连接” ==========
    // ========================================================================
    private fun hookConnectionState(module: XposedModule, param: PackageReadyParam) {
        val wrapperClassNames = listOf(
            "com.meizu.earphone.audiodo.mzbluetooth.MzBluetoothWrapper",
            "com.meizu.bluetooth.sdk.MzBluetoothManager",
        )
        for (className in wrapperClassNames) {
            try {
                val cls = param.classLoader.loadClass(className)

                runCatching {
                    val m = cls.getDeclaredMethod("isConnected", String::class.java)
                    module.hook(m).intercept { chain ->
                        val id = chain.args[0] as? String
                        if (id != null && id.startsWith("fake_")) true else chain.proceed()
                    }
                }

                runCatching {
                    val m = cls.getDeclaredMethod("getConnectionState", String::class.java)
                    module.hook(m).intercept { chain ->
                        val id = chain.args[0] as? String
                        // BluetoothProfile.STATE_CONNECTED
                        if (id != null && id.startsWith("fake_")) 2 else chain.proceed()
                    }
                }

                XLog.i(module, TAG, "hookConnectionState ok: $className")
            } catch (e: Throwable) {
                XLog.e(module, TAG, "hookConnectionState failed: $className", e)
            }
        }
    }

    // ========================================================================
    // ========== TWSControlService.onCreate：注册广播接收器 ==========
    // ========================================================================
    private fun hookTwsControlService(module: XposedModule, param: PackageReadyParam) {
        try {
            val serviceClass = param.classLoader.loadClass(
                "com.meizu.earphone.biz.devicecontrol.TWSControlService"
            )
            val onCreate = serviceClass.getDeclaredMethod("onCreate")

            module.hook(onCreate).intercept { chain ->
                chain.proceed()

                val service = chain.thisObject as? Context
                if (service != null) {
                    try {
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context?, intent: Intent?) {
                                val left = intent?.getIntExtra("left", -1) ?: -1
                                val right = intent?.getIntExtra("right", -1) ?: -1
                                val box = intent?.getIntExtra("box", -1) ?: -1

                                if (left >= 0) earbudLeft = left
                                if (right >= 0) earbudRight = right
                                if (box >= 0) earbudBox = box
                            }
                        }
                        val filter = IntentFilter(ACTION_BATTERY)
                        if (Build.VERSION.SDK_INT >= 33) {
                            service.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                        } else {
                            service.registerReceiver(receiver, filter)
                        }
                    } catch (e: Throwable) {}

                    try {
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context?, intent: Intent?) {
                                val name = intent?.getStringExtra("name")
                                val mac = intent?.getStringExtra("mac")

                                if (!name.isNullOrEmpty()) {
                                    currentName = name
                                }
                                if (!mac.isNullOrEmpty()) {
                                    currentMac = mac
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
            XLog.e(module, TAG, "hookTwsControlService failed", e)
        }
    }

    // ========================================================================
    // ========== hook queryBattery() ==========
    // ========================================================================
    private fun hookQueryBattery(module: XposedModule, param: PackageReadyParam) {
        try {
            val wrapperClass = param.classLoader.loadClass(
                "com.meizu.earphone.audiodo.mzbluetooth.MzBluetoothWrapper"
            )
            val queryMethod = wrapperClass.getDeclaredMethod("queryBattery", String::class.java)
            val electricityClass = param.classLoader.loadClass(
                "com.meizu.bluetooth.sdk.ElectricityOfDevice"
            )

            module.hook(queryMethod).intercept { chain ->
                val deviceId = chain.args[0] as? String
                val left = earbudLeft
                val right = earbudRight
                val box = earbudBox

                if (deviceId != null && deviceId.startsWith("fake_")) {
                    if (left >= 0 || right >= 0 || box >= 0) {
                        try {
                            val elec = electricityClass.getConstructor(
                                Int::class.javaPrimitiveType,
                                Boolean::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType,
                                Boolean::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType,
                                Boolean::class.javaPrimitiveType
                            ).newInstance(
                                if (box >= 0) box else -1, false,
                                if (left >= 0) left else -1, false,
                                if (right >= 0) right else -1, false
                            )
                            return@intercept elec
                        } catch (e: Throwable) {}
                    }
                }
                chain.proceed()
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookQueryBattery failed", e)
        }
    }

    // ========================================================================
    // ========== 佩戴状态：强制已佩戴（控制中心切换模式需要） ==========
    // ========================================================================
    private fun hookWearingStatus(module: XposedModule, param: PackageReadyParam) {
        try {
            val statusClass = param.classLoader.loadClass(
                "com.meizu.bluetooth.sdk.EarphoneWearingStatus"
            )

            val isLeft = statusClass.getDeclaredMethod("isLeftEarphoneWearing")
            module.hook(isLeft).intercept { true }

            val isRight = statusClass.getDeclaredMethod("isRightEarphoneWearing")
            module.hook(isRight).intercept { true }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookWearingStatus failed", e)
        }
    }

    // ========================================================================
    // ========== hook ElectricityOfDevice 的 getter ==========
    // ========================================================================
    private fun hookElectricityGetters(module: XposedModule, param: PackageReadyParam) {
        try {
            val electricityClass = param.classLoader.loadClass(
                "com.meizu.bluetooth.sdk.ElectricityOfDevice"
            )

            val getLeft = electricityClass.getDeclaredMethod("getLeftEarphone")
            module.hook(getLeft).intercept { chain ->
                val left = earbudLeft
                if (left >= 0) return@intercept left
                chain.proceed()
            }

            val getRight = electricityClass.getDeclaredMethod("getRightEarphone")
            module.hook(getRight).intercept { chain ->
                val right = earbudRight
                if (right >= 0) return@intercept right
                chain.proceed()
            }

            val getBox = electricityClass.getDeclaredMethod("getEarphoneBox")
            module.hook(getBox).intercept { chain ->
                val box = earbudBox
                if (box >= 0) return@intercept box
                chain.proceed()
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookElectricityGetters failed", e)
        }
    }

    // ========================================================================
    // ========== 注入设备列表 ==========
    // ========================================================================
    private fun hookDeviceList(module: XposedModule, param: PackageReadyParam) {
        try {
            val dClass = try {
                param.classLoader.loadClass("n5.e").takeIf {
                    runCatching { it.getDeclaredMethod("c") }.isSuccess
                }
            } catch (e: Throwable) { null } ?: param.classLoader.loadClass("n5.d")
            val cMethod = dClass.getDeclaredMethod("c")
            val k5dClass = param.classLoader.loadClass("k5.d")
            val k5dCtor = k5dClass.getConstructor(String::class.java, String::class.java)

            module.hook(cMethod).intercept { chain ->
                val result = chain.proceed()
                if (result !is MutableList<*>) return@intercept result

                @Suppress("UNCHECKED_CAST")
                val original = result as MutableList<Any?>

                val deviceAddress = currentMac ?: return@intercept original
                val deviceName = currentName ?: "vivo TWS"
                val fakeDeviceId = "fake_" + deviceAddress.replace(":", "")

                val exists = original.any {
                    try {
                        val aField = it!!.javaClass.getDeclaredField("a")
                        aField.isAccessible = true
                        aField.get(it) == fakeDeviceId
                    } catch (e: Throwable) {
                        false
                    }
                }

                if (!exists) {
                    val fake = k5dCtor.newInstance(fakeDeviceId, deviceName)
                    original.add(fake)
                }

                original
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookDeviceList failed", e)
        }
    }

    // ========================================================================
    // ========== hook RemotePrefServiceImpl.generateStateModel() ==========
    // ========================================================================
    private fun hookRemotePrefService(module: XposedModule, param: PackageReadyParam) {
        try {
            val implClass = param.classLoader.loadClass(
                "com.meizu.earphone.biz.devicecontrol.remote.RemotePrefServiceImpl"
            )

            val method = implClass.getDeclaredMethod("generateStateModel", String::class.java)
            val statusClass = param.classLoader.loadClass(
                "com.meizu.earphone.audiodo.model.AudioFunctionStatusModel"
            )
            val electricityClass = param.classLoader.loadClass(
                "com.meizu.bluetooth.sdk.ElectricityOfDevice"
            )
            val aField = statusClass.getDeclaredField("a").apply { isAccessible = true }
            val cField = statusClass.getDeclaredField("c").apply { isAccessible = true }

            module.hook(method).intercept { chain ->
                val deviceId = chain.args[0] as? String
                val result = chain.proceed() as? Any

                if (result != null && deviceId != null && deviceId.startsWith("fake_")) {
                    val left = earbudLeft
                    val right = earbudRight
                    val box = earbudBox

                    if (left >= 0 || right >= 0 || box >= 0) {
                        try {
                            val elec = electricityClass.getConstructor(
                                Int::class.javaPrimitiveType,
                                Boolean::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType,
                                Boolean::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType,
                                Boolean::class.javaPrimitiveType
                            ).newInstance(
                                if (box >= 0) box else -1, false,
                                if (left >= 0) left else -1, false,
                                if (right >= 0) right else -1, false
                            )
                            aField.set(result, elec)
                        } catch (e: Throwable) {}
                    }

                    try {
                        cField.setInt(result, currentAncMode)
                    } catch (e: Throwable) {}
                }

                result
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookRemotePrefService failed", e)
        }
    }

    // ========================================================================
    // ========== hook setAncSwitch，记录模式 + 发广播给蓝牙进程 ==========
    // ========================================================================
    private fun hookSetAncSwitch(module: XposedModule, param: PackageReadyParam) {
        try {
            val wrapperClass = param.classLoader.loadClass(
                "com.meizu.earphone.audiodo.mzbluetooth.MzBluetoothWrapper"
            )
            val method = wrapperClass.getDeclaredMethod(
                "setAncSwitch",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            val contextField = wrapperClass.getDeclaredField("context").apply { isAccessible = true }

            module.hook(method).intercept { chain ->
                val deviceId = chain.args[0] as? String
                val mode = chain.args[1] as Int

                if (deviceId != null && deviceId.startsWith("fake_")) {
                    currentAncMode = mode

                    try {
                        val context = contextField.get(chain.thisObject) as? Context
                        if (context != null) {
                            val intent = Intent(ACTION_SET_ANC)
                            intent.putExtra("mode", mode)
                            intent.setPackage("com.android.bluetooth")
                            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                            intent.addFlags(0x01000000)
                            context.sendBroadcast(intent)
                        }
                    } catch (e: Throwable) {}

                    return@intercept true
                }

                chain.proceed()
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookSetAncSwitch failed", e)
        }
    }
}
