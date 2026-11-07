package com.frost.ears.utils

import android.util.Log
import io.github.libxposed.api.XposedModule

object XLog {
    
    fun e(module: XposedModule, tag: String, msg: String, e: Throwable? = null) {
        module.log(Log.ERROR, tag, msg, e)
    }
    
    fun i(module: XposedModule, tag: String, msg: String) {
        module.log(Log.INFO, tag, msg)
    }
}