package com.frost.ears.hook

import android.util.Log
import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import com.frost.ears.hook.TWS.*

class MainHook : XposedModule() {

    companion object {
        private const val MODULE_PACKAGE_NAME = "com.frost.ears"
        private const val SYSTEMUI_PACKAGE_NAME = "com.android.systemui"
        private const val EARPHONE_PACKAGE_NAME = "com.meizu.earphone"
        private const val BLUETOOTH_PACKAGE_NAME = "com.android.bluetooth"
        private const val MEIZUIOT_PACKAGE_NAME = "com.meizu.meizuiot"
        
        var moduleContext: Context? = null
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return

        when (param.packageName) {
            SYSTEMUI_PACKAGE_NAME -> EarbudHook.hook(this, param)
            EARPHONE_PACKAGE_NAME -> MeizuTWS.hook(this, param)
            BLUETOOTH_PACKAGE_NAME -> BluetoothHook.hook(this, param)
            MEIZUIOT_PACKAGE_NAME -> MeizuiotTWS.hook(this, param)
        }
    }
}