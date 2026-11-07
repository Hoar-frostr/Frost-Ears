package com.frost.ears.hook.TWS

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import com.frost.ears.utils.XLog

/**
 * 魅族系统 UI / 控制中心 hook。
 * 1. [hookMoreButton]：耳机卡片"更多设置"→ 参考 openControlApp 打开对应品牌官方 app，
 *    未装/未识别时兜底系统蓝牙设备详情页。
 * 2. [hookDeviceCenterSpacing]：设备中心标题/列表间距微调。
 * 当前耳机名/MAC 来自蓝牙进程 [BluetoothHook] 的 EARBUD_DEVICE_INFO 广播，缓存于本进程。
 */
object EarbudHook {

    private const val TAG = "MzfpHook"

    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val ACTION_BLUETOOTH_DEVICE_DETAIL_SETTINGS =
        "com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS"
    private const val EXTRA_DEVICE_ADDRESS = "device_address"
    private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"

    @Volatile private var currentMac: String? = null
    @Volatile private var currentName: String? = null
    private var receiverRegistered = false
    private val receiverLock = Any()

    fun hook(module: XposedModule, param: PackageReadyParam) {
        if (!module.getRemotePreferences("frost_tool_prefs").getBoolean("tws_device_spoof_enabled", false)) return
        hookMoreButton(module, param)
        hookDeviceCenterSpacing(module, param)
        hookReplaceDeviceIcon(module, param)
        hookReplaceEarphoneBoxIcon(module, param)
    }
    
    private fun loadCustomTwsIcon(context: Context?, controlId: String?): android.graphics.Bitmap? {
        if (context == null || controlId == null || !controlId.startsWith("fake_")) return null
        return try {
            val uri = android.net.Uri.parse("content://com.frost.ears.icon/$controlId")
            context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun hookReplaceDeviceIcon(module: XposedModule, param: PackageReadyParam) {
        try {
            val infoClass = param.classLoader.loadClass("com.flyme.systemui.controls.ui.model.MzControlInfo")
            val getIconMethod = infoClass.getDeclaredMethod("getIcon")

            module.hook(getIconMethod).intercept { chain ->
                val info = chain.thisObject
                val result = chain.proceed()

                try {
                    val control = infoClass.getMethod("getControl").invoke(info)
                    val controlId = control.javaClass.getMethod("getControlId").invoke(control) as? String
        
                    if (controlId != null && controlId.startsWith("fake_")) {
                        val context = infoClass.getMethod("getContext").invoke(info) as? Context
                        val bitmap = loadCustomTwsIcon(context, controlId)
                        if (bitmap != null && context != null) {
                            return@intercept android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                        }
                    }
                } catch (e: Throwable) {}
                result
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookReplaceDeviceIcon failed", e)
        }
    }

    private fun hookReplaceEarphoneBoxIcon(module: XposedModule, param: PackageReadyParam) {
        try {
            val proxyClass = param.classLoader.loadClass("com.flyme.qs.CustomTilePluginReflectProxy")
            val method = proxyClass.getDeclaredMethod("getDialogView", String::class.java)
            method.isAccessible = true

            module.hook(method).intercept { chain ->
                val json = chain.args[0] as? String
                val result = chain.proceed() as? android.view.View

                if (result != null) {
                    try {
                        fun findImageViewByName(root: android.view.View, name: String): android.widget.ImageView? {
                            if (root is android.widget.ImageView) {
                                try {
                                    if (root.resources.getResourceEntryName(root.id) == name) {
                                        return root
                                    }
                                } catch (e: Throwable) {}
                            }
                            if (root is android.view.ViewGroup) {
                                for (i in 0 until root.childCount) {
                                    val found = findImageViewByName(root.getChildAt(i), name)
                                    if (found != null) return found
                                }
                            }
                            return null
                        }

                        val target = findImageViewByName(result, "iv_plugin_earphone_box")

                        if (target != null) {
                            val bitmap = loadCustomTwsIcon(result.context, json)
                            if (bitmap != null) {
                                target.setImageBitmap(bitmap)
                            }
                        }
                    } catch (e: Throwable) {}
                }
                result
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hook getDialogView failed", e)
        }
    }
    
    private fun hookMoreButton(module: XposedModule, param: PackageReadyParam) {
        try {
            val proxyClass = param.classLoader.loadClass("com.flyme.qs.CustomTilePluginReflectProxy")
            val method = proxyClass.getDeclaredMethod("getMoreButtonIntent")
            val sysUICtxField = proxyClass.getDeclaredField("mSysUICtx").apply { isAccessible = true }

            module.hook(method).intercept { chain ->
                val proxy = chain.thisObject
                val context = sysUICtxField.get(proxy) as? Context

                if (context != null) {
                    registerDeviceInfoReceiverOnce(context.applicationContext)

                    val apps = EarbudCatalog.matchByDeviceName(currentName)
                    if (apps != null) {
                        buildControlAppIntent(context, apps)?.let {
                            return@intercept it
                        }
                        buildDeviceDetailIntent(context, currentMac)?.let {
                            return@intercept it
                        }
                    }
                }

                chain.proceed()
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookMoreButton failed: ${e.message}", e)
        }
    }

    /**
     * 与 HyperEars 一致：只打开厂商 app 的应用首页（launcher 入口），
     * 让 app 自己完成启动流程并扫描已连接耳机；直接跳内部页面会因缺少设备状态而白屏。
     */
    private fun buildControlAppIntent(context: Context, apps: List<EarbudCatalog.App>): Intent? {
        for (app in apps) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    return intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
            } catch (e: Throwable) {
            }
        }
        return null
    }

    private fun buildDeviceDetailIntent(context: Context, mac: String?): Intent? {
        if (mac == null) return null
        val fragmentArguments = Bundle().apply {
            putString(EXTRA_DEVICE_ADDRESS, mac)
        }
        val deviceDetails = Intent(ACTION_BLUETOOTH_DEVICE_DETAIL_SETTINGS)
            .setPackage(SETTINGS_PACKAGE)
            .putExtra(EXTRA_DEVICE_ADDRESS, mac)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArguments)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (!canResolve(context, deviceDetails)) return null
        return deviceDetails
    }

    private fun canResolve(context: Context, intent: Intent): Boolean =
        try {
            context.packageManager.resolveActivity(intent, 0) != null
        } catch (e: Throwable) {
            false
        }

    private fun registerDeviceInfoReceiverOnce(context: Context) {
        synchronized(receiverLock) {
            if (receiverRegistered) return
            receiverRegistered = true
        }
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val mac = intent?.getStringExtra("mac")
                    val name = intent?.getStringExtra("name")
                    if (!mac.isNullOrEmpty()) {
                        currentMac = mac
                    }
                    if (!name.isNullOrEmpty()) {
                        currentName = name
                    }
                }
            }
            val filter = IntentFilter(TwsActions.ACTION_DEVICE_INFO)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Throwable) {}
    }

    private fun hookDeviceCenterSpacing(module: XposedModule, param: PackageReadyParam) {
        try {
            val containerCl = param.classLoader.loadClass(
                "com.flyme.systemui.controls.DeviceCenterContainer"
            )

            val onFinishInflate = containerCl.getDeclaredMethod("onFinishInflate")
            module.hook(onFinishInflate).intercept { chain ->
                val result = chain.proceed()
                try {
                    val container = chain.thisObject as? ViewGroup ?: return@intercept result
                    applySpacing(container)
                } catch (e: Throwable) {
                    XLog.e(module, TAG, "applySpacing failed: ${e.message}", e)
                }
                result
            }
        } catch (e: Throwable) {
            XLog.e(module, TAG, "hookDeviceCenterSpacing failed: ${e.message}", e)
        }
    }

    private fun applySpacing(container: ViewGroup) {
        val density = container.resources.displayMetrics.density
        val pkg = "com.android.systemui"

        val headerTextId = container.resources.getIdentifier(
            "device_center_header_text", "id", pkg
        )
        container.findViewById<View>(headerTextId)?.let {
            it.translationX = 7f * density
            it.translationY = 2f * density
        }

        val listId = container.resources.getIdentifier(
            "device_center_list", "id", pkg
        )
        container.findViewById<View>(listId)?.let {
            it.translationY = 7f * density
        }
    }
}
