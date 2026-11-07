package com.frost.ears.hook.TWS

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import com.frost.ears.utils.XLog
import java.util.UUID

/**
 * 蓝牙进程 hook：注册广播、维护当前设备、按品牌协议常驻读循环（电量）+ 降噪写帧。
 * 支持的品牌协议：vivo（GAIA）、原道 OriG in（NiceHck 二进制 RFCOMM）。
 * 品牌差异集中在 [ProtocolKind] 路由。
 */
object BluetoothHook {

    private const val TAG = "MzfpHook"

    // 与耳机 app（MeizuTWS / MeizuiotTWS）约定的广播动作，统一命名见 TwsActions
    private const val ACTION_DEVICE_INFO = TwsActions.ACTION_DEVICE_INFO
    private const val ACTION_BATTERY = TwsActions.ACTION_BATTERY
    private const val ACTION_SET_ANC = TwsActions.ACTION_SET_ANC

    // 原道 OriG in 的 RFCOMM SPP 服务 UUID
    private const val NICEHCK_SPP_UUID = "0000a100-1000-8000-4e48-434b4354524c"

    // 华为 FreeBuds / FreeClip 使用标准 SPP 服务
    private const val HUAWEI_SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"

    // 常驻读循环参数
    private const val POLL_INTERVAL_MS = 30_000L   // 周期主动查电量间隔
    private const val READ_BUFFER_SIZE = 256

    /** 当前使用的品牌协议。 */
    private enum class ProtocolKind {
        GAIA,      // vivo / iQOO
        NICEHCK,   // 原道 OriG in
        HUAWEI,    // 华为 FreeBuds / FreeClip
    }
    
    @Volatile private var poppedMac: String? = null

    @Volatile private var module: XposedModule? = null
    @Volatile private var context: Context? = null

    @Volatile private var currentDevice: BluetoothDevice? = null
    @Volatile private var currentProtocol: ProtocolKind? = null
    @Volatile private var socket: BluetoothSocket? = null
    private val socketLock = Any()

    // 常驻读循环状态
    @Volatile private var readLoopActive = false
    private val readLoopLock = Any()

    fun hook(module: XposedModule, param: PackageReadyParam) {
        if (!module.getRemotePreferences("frost_tool_prefs").getBoolean("tws_device_spoof_enabled", false)) return
        this.module = module
        hookAdapterService(module, param)
        hookA2dpState(module, param)
        hookFastPairDialogImage(module, param)
    }

    // ========================================================================
    // ========== AdapterService.onCreate：拿 Context + 注册广播接收器 ==========
    // ========================================================================
    private fun hookAdapterService(module: XposedModule, param: PackageReadyParam) {
        try {
            val serviceClass = param.classLoader.loadClass(
                "com.android.bluetooth.btservice.AdapterService"
            )
            val onCreate = serviceClass.getDeclaredMethod("onCreate")

            module.hook(onCreate).intercept { chain ->
                chain.proceed()

                val service = chain.thisObject as? Context
                if (service != null) {
                    context = service
                    registerReceivers(module, service)
                }
                null
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookAdapterService failed", e)
        }
    }

    // ========================================================================
    // ========== 注册广播接收器 ==========
    // ========================================================================
    private fun registerReceivers(module: XposedModule, service: Context) {
        // 收魅族耳机 app 的降噪设置广播
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val mode = intent?.getIntExtra("mode", -1) ?: -1
                    if (mode < 0) return

                    val device = currentDevice ?: findEarbudDevice()
                    if (device == null) return
                    val protocol = currentProtocol ?: resolveProtocol(device)
                    if (protocol == null) return

                    // connect/write 会阻塞，放到子线程
                    Thread {
                        sendNoiseMode(device, protocol, mode)
                    }.start()
                }
            }
            val filter = IntentFilter(ACTION_SET_ANC)
            registerReceiverCompat(service, receiver, filter)
        } catch (e: Throwable) {
            XLog.e(module, TAG, "register set-anc receiver failed", e)
        }
    }

    // ========================================================================
    // ========== A2dpService.handleConnectionStateChanged：维护当前设备 ==========
    // ========================================================================
    private fun hookA2dpState(module: XposedModule, param: PackageReadyParam) {
        try {
            val serviceClass = param.classLoader.loadClass(
                "com.android.bluetooth.a2dp.A2dpService"
            )
            val method = serviceClass.getDeclaredMethod(
                "handleConnectionStateChanged",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            module.hook(method).intercept { chain ->
                val device = chain.args[0] as? BluetoothDevice
                val state = chain.args[2] as Int
                if (device != null) {
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            currentDevice = device
                            currentProtocol = resolveProtocol(device)
                            notifyDeviceInfo(device)
                            poppedMac = null          // 复位：本次连接还没弹过

                            // 启动常驻读循环；电量一到 → notifyBattery → 弹窗
                            val protocol = currentProtocol
                            if (protocol != null) {
                                startReadLoop(device, protocol)
                            }
                        }
                        BluetoothProfile.STATE_DISCONNECTED,
                        BluetoothProfile.STATE_DISCONNECTING -> {
                            if (device == currentDevice) {
                                currentDevice = null
                                closeSocket()
                            }
                            poppedMac = null          // 断开复位，下次连接还能弹
                        }
                    }
                }
                chain.proceed()
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookA2dpState failed", e)
        }
    }
    // ========================================================================
    // ========== 协议路由：按设备名选择品牌协议 ==========
    // ========================================================================
    private fun resolveProtocol(device: BluetoothDevice): ProtocolKind? {
        val brand = EarbudCatalog.brandOfDevice(deviceName(device))
        return when (brand) {
            "vivo" -> ProtocolKind.GAIA
            "nicehck" -> ProtocolKind.NICEHCK
            "huawei" -> ProtocolKind.HUAWEI
            else -> null
        }
    }

    // ========================================================================
    // ========== 设备信息广播（name / MAC） ==========
    // ========================================================================
    private fun notifyDeviceInfo(device: BluetoothDevice) {
        val ctx = context ?: return
        val name = deviceName(device) ?: return
        try {
            val intent = Intent(ACTION_DEVICE_INFO)
            intent.putExtra("name", name)
            intent.putExtra("mac", device.address)
            sendBroadcastCompat(ctx, intent)
        } catch (e: Throwable) {
            module?.let { XLog.e(it, TAG, "notifyDeviceInfo failed", e) }
        }
    }

    // ========================================================================
    // ========== 常驻读循环 + 周期轮询（实时刷新电量） ==========
    // ========================================================================
    private fun startReadLoop(device: BluetoothDevice, protocol: ProtocolKind) {
        synchronized(readLoopLock) {
            if (readLoopActive) return
            readLoopActive = true
        }
        // 写线程：周期主动查询电量（兜底）
        Thread {
            while (readLoopActive && device == currentDevice) {
                try {
                    val sock = ensureSocket(device, protocol) ?: break
                    try {
                        sock.outputStream.write(batteryQuery(protocol))
                        sock.outputStream.flush()
                    } catch (e: Throwable) {
                    }
                } catch (e: Throwable) {
                    module?.let { XLog.e(it, TAG, "poll failed", e) }
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.start()

        // 读线程：解析电量上报并广播
        Thread {
            try {
                val sock = ensureSocket(device, protocol) ?: return@Thread
                val input = sock.inputStream
                val buf = ByteArray(READ_BUFFER_SIZE)
                try {
                    sock.outputStream.write(batteryQuery(protocol))
                    sock.outputStream.flush()
                } catch (e: Throwable) {
                }
                val gaiaDecoder = GaiaProtocol.Decoder()
                val niceDecoder = NiceHckProtocol.Decoder()
                val huaweiDecoder = HuaweiProtocol.Decoder()
                while (readLoopActive && device == currentDevice) {
                    val n = try {
                        input.read(buf)
                    } catch (e: Throwable) {
                        -1
                    }
                    if (n < 0) break
                    when (protocol) {
                        ProtocolKind.GAIA -> gaiaDecoder.offer(buf.copyOfRange(0, n))
                            .forEach { frame ->
                                GaiaProtocol.parseBatteryState(frame, gaiaBrandConfig(device))
                                    ?.let { notifyBattery(it.leftPercent, it.rightPercent, it.casePercent) }
                            }
                        ProtocolKind.NICEHCK -> niceDecoder.offer(buf.copyOfRange(0, n))
                            .forEach { frame ->
                                NiceHckProtocol.parseBattery(frame)
                                    ?.let { notifyBattery(it.leftPercent, it.rightPercent, it.casePercent) }
                            }
                        ProtocolKind.HUAWEI -> huaweiDecoder.offer(buf.copyOfRange(0, n))
                            .forEach { frame ->
                                HuaweiProtocol.parseBattery(frame)
                                    ?.let { notifyBattery(it.leftPercent, it.rightPercent, it.casePercent) }
                            }
                    }
                }
            } catch (e: Throwable) {
                module?.let { XLog.e(it, TAG, "read loop failed", e) }
            } finally {
                synchronized(readLoopLock) {
                    readLoopActive = false
                }
                closeSocket()
            }
        }.start()
    }

    /** 按协议返回“查询电量”命令字节。 */
    private fun batteryQuery(protocol: ProtocolKind): ByteArray = when (protocol) {
        ProtocolKind.GAIA -> GaiaProtocol.queryBattery(GaiaProtocol.BrandConfig.VIVO_DEFAULT_V4)
        ProtocolKind.NICEHCK -> NiceHckProtocol.queryBattery()
        ProtocolKind.HUAWEI -> HuaweiProtocol.queryBattery
    }

    private fun gaiaBrandConfig(device: BluetoothDevice): GaiaProtocol.BrandConfig {
        val name = deviceName(device)?.lowercase()
        return when {
            name?.contains("air3pro") == true -> GaiaProtocol.BrandConfig.VIVO_AIR3_PRO_V3
            name?.contains("tws3e") == true -> GaiaProtocol.BrandConfig.VIVO_TWS3E_V3
            else -> GaiaProtocol.BrandConfig.VIVO_DEFAULT_V4
        }
    }

    private fun notifyBattery(left: Int?, right: Int?, box: Int?) {
        val ctx = context ?: return
        val l = left ?: -1
        val r = right ?: -1
        val b = box ?: -1
        if (l < 0 && r < 0 && b < 0) return

        try {
            val intent = Intent(ACTION_BATTERY)
            intent.putExtra("left", l)
            intent.putExtra("right", r)
            intent.putExtra("box", b)
            sendBroadcastCompat(ctx, intent)
        } catch (e: Throwable) {
            module?.let { XLog.e(it, TAG, "notifyBattery failed", e) }
        }
        
        notifyMzfpBattery(l, r, b)
        currentDevice?.let { dev ->
            if (poppedMac != dev.address) {
                poppedMac = dev.address
                MeizuFastPairPop.show(context, dev, l, r, b, module)
            }
        }
    }
    
    private fun notifyMzfpBattery(left: Int, right: Int, box: Int) {
        val ctx = context ?: return
        try {
            val mzIntent = Intent("com.meizu.bluetooth.mzfp.BATTERY_CHANGED")
            mzIntent.putExtra("batteryLeft",  left)
            mzIntent.putExtra("batteryRight", right)
            mzIntent.putExtra("batteryBox",   box)
            mzIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(mzIntent)
        } catch (e: Throwable) {
            module?.let { XLog.e(it, TAG, "notifyMzfpBattery failed", e) }
        }
    }

    // ========================================================================
    // ========== 发送降噪模式 ==========
    // ========================================================================
    private fun sendNoiseMode(device: BluetoothDevice, protocol: ProtocolKind, mode: Int) {
        try {
            val sock = ensureSocket(device, protocol) ?: return
            val out = sock.outputStream
            when (protocol) {
                ProtocolKind.GAIA -> {
                    // 魅族模式 -> GAIA 枚举：1=OFF(1) 2=ANC(0) 3=TRANSPARENCY(2)
                    val wire = when (mode) {
                        1 -> GaiaProtocol.NoiseMode.OFF
                        2 -> GaiaProtocol.NoiseMode.ANC
                        3 -> GaiaProtocol.NoiseMode.TRANSPARENCY
                        else -> return
                    }
                    try {
                        out.write(GaiaProtocol.handshake(GaiaProtocol.BrandConfig.VIVO_DEFAULT_V4))
                        out.flush()
                        Thread.sleep(80)
                    } catch (e: Throwable) {
                    }
                    out.write(GaiaProtocol.setNoiseMode(wire, gaiaBrandConfig(device)))
                    out.flush()
                }
                ProtocolKind.NICEHCK -> {
                    // 魅族模式 -> 原道枚举：1=OFF(0x00) 2=ANC(0x02) 3=TRANSPARENCY(0x01)
                    val wire = when (mode) {
                        1 -> NiceHckProtocol.NoiseMode.OFF
                        2 -> NiceHckProtocol.NoiseMode.ANC
                        3 -> NiceHckProtocol.NoiseMode.TRANSPARENCY
                        else -> return
                    }
                    out.write(NiceHckProtocol.setNoiseMode(wire))
                    out.flush()
                }
                ProtocolKind.HUAWEI -> {
                    // 魅族模式 -> 华为枚举：1=OFF(0x00) 2=ANC(0x01) 3=TRANSPARENCY(0x02)
                    val wire = when (mode) {
                        1 -> HuaweiProtocol.NoiseMode.OFF
                        2 -> HuaweiProtocol.NoiseMode.ANC
                        3 -> HuaweiProtocol.NoiseMode.TRANSPARENCY
                        else -> return
                    }
                    out.write(HuaweiProtocol.setNoiseMode(wire))
                    out.flush()
                }
            }
        } catch (e: Throwable) {
            module?.let { XLog.e(it, TAG, "sendNoiseMode failed", e) }
            closeSocket()
        }
    }

    // ========================================================================
    // ========== RFCOMM socket 管理 ==========
    // ========================================================================
    private fun ensureSocket(device: BluetoothDevice, protocol: ProtocolKind): BluetoothSocket? {
        synchronized(socketLock) {
            var sock = socket
            if (sock != null && sock.isConnected) return sock

            val serviceUuid = when (protocol) {
                ProtocolKind.GAIA -> "00000837-d102-11e1-9b23-00025b00a5a5"
                ProtocolKind.NICEHCK -> NICEHCK_SPP_UUID
                ProtocolKind.HUAWEI -> HUAWEI_SPP_UUID
            }
            return try {
                sock = device.createRfcommSocketToServiceRecord(
                    UUID.fromString(serviceUuid)
                )
                sock.connect()
                socket = sock
                sock
            } catch (e: Throwable) {
                module?.let { XLog.e(it, TAG, "connect failed", e) }
                socket = null
                null
            }
        }
    }

    private fun closeSocket() {
        synchronized(socketLock) {
            try {
                socket?.close()
            } catch (e: Throwable) {
            }
            socket = null
        }
    }

    // ========================================================================
    // ========== 从已配对设备里找同类耳机（兜底） ==========
    // ========================================================================
    private fun findEarbudDevice(): BluetoothDevice? {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            for (device in adapter.bondedDevices) {
                if (resolveProtocol(device) != null) {
                    return device
                }
            }
            null
        } catch (e: Throwable) {
            null
        }
    }
    
    // ========================================================================
    // ========== 自定义弹窗耳机图（仓 / 左耳 / 右耳） ==========
    // ========================================================================

    /** 读图：走模块 app 的 ContentProvider（authority: com.frost.ears.icon） */
    private fun loadCustomTwsIcon(controlId: String?): android.graphics.Bitmap? {
        if (controlId == null || !controlId.startsWith("fake_")) return null

        val ctx = context ?: return null
        return try {
            val uri = android.net.Uri.parse("content://com.frost.ears.icon/$controlId")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        } catch (e: Throwable) {
            module?.let { XLog.e(it, TAG, "provider read failed: $controlId", e) }
            null
        }
    }

    /** 由当前设备 MAC 生成 controlId */
    private fun currentControlId(device: BluetoothDevice?): String? {
        val mac = device?.address ?: return null
        return "fake_" + mac.replace(":", "")
    }

    private fun hookFastPairDialogImage(module: XposedModule, param: PackageReadyParam) {
        try {
            val vsCls = param.classLoader.loadClass(
                "com.android.bluetooth.fastpair.MzFastPairDialog\$ViewSwitcher"
            )
            val switchView = vsCls.getDeclaredMethod("switchView", String::class.java)

            module.hook(switchView).intercept { chain ->
                val result = chain.proceed()
                try {
                    replaceEarbudImages(chain.thisObject)
                } catch (e: Throwable) {
                    XLog.e(module, TAG, "replaceEarbudImages failed", e)
                }
                result
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookFastPairDialogImage failed", e)
        }
    }

    private fun replaceEarbudImages(viewSwitcher: Any?) {
        if (viewSwitcher == null) return

        val dialog = viewSwitcher.javaClass.declaredFields
            .firstOrNull { it.type.name == "com.android.bluetooth.fastpair.MzFastPairDialog" }
            ?.apply { isAccessible = true }
            ?.get(viewSwitcher) ?: return

        val root = dialog.javaClass.declaredFields
            .firstOrNull { it.type == android.view.View::class.java }
            ?.apply { isAccessible = true }
            ?.get(dialog) as? android.view.View ?: return

        val baseId = currentControlId(currentDevice) ?: return
        val res = root.resources
        val pkg = "com.android.bluetooth"

        replaceOne(root, res, pkg, "left_pop",   baseId)         // 耳机仓
        replaceOne(root, res, pkg, "middle_pop", baseId + "_l")  // 左耳
        replaceOne(root, res, pkg, "right_pop",  baseId + "_r")  // 右耳
    }

    private fun replaceOne(
        root: android.view.View,
        res: android.content.res.Resources,
        pkg: String,
        containerIdName: String,
        controlId: String
    ) {
        val id = res.getIdentifier(containerIdName, "id", pkg)
        if (id == 0) return

        val container = root.findViewById<android.view.View>(id) ?: return
        val bitmap = loadCustomTwsIcon(controlId) ?: return
        val target = findProductImage(container) ?: return

        target.setImageBitmap(bitmap)
        target.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER)
    }

    private fun findProductImage(container: android.view.View): android.widget.ImageView? {
        val group = container as? android.view.ViewGroup ?: return null
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is android.widget.ImageView) return child
        }
        return null
    }
    
    // ========================================================================
    // ========== 兼容工具 ==========
    // ========================================================================
    private fun deviceName(device: BluetoothDevice): String? {
        return try {
            device.name ?: device.alias
        } catch (e: Throwable) {
            null
        }
    }

    private fun registerReceiverCompat(ctx: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, filter)
            }
        } catch (e: Throwable) {
            module?.let { XLog.e(it, TAG, "registerReceiver failed", e) }
        }
    }

    private fun sendBroadcastCompat(ctx: Context, intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(intent)
        } catch (e: Throwable) {
            module?.let { XLog.e(it, TAG, "sendBroadcast failed", e) }
        }
    }
}
