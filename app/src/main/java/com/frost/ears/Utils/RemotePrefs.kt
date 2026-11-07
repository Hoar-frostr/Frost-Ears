package com.frost.ears.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import io.github.libxposed.service.XposedService
import org.json.JSONObject

object RemotePrefs {
    
    private const val PREFS_NAME = "frost_tool_prefs"
    
    private fun getPrefs(): SharedPreferences? {
        return App.mService?.getRemotePreferences(PREFS_NAME)
    }
    
    fun putBoolean(key: String, value: Boolean) {
        getPrefs()?.edit()?.putBoolean(key, value)?.apply()
    }
    
    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return getPrefs()?.getBoolean(key, default) ?: default
    }
    
    fun putInt(key: String, value: Int) {
        getPrefs()?.edit()?.putInt(key, value)?.apply()
    }
    
    fun getInt(key: String, default: Int = 0): Int {
        return getPrefs()?.getInt(key, default) ?: default
    }
    
    fun putString(key: String, value: String) {
        getPrefs()?.edit()?.putString(key, value)?.apply()
    }
    
    fun getString(key: String, default: String = ""): String {
        return getPrefs()?.getString(key, default) ?: default
    }
    
    fun putFloat(key: String, value: Float) {
        getPrefs()?.edit()?.putFloat(key, value)?.apply()
    }
    
    fun getFloat(key: String, default: Float = 0f): Float {
        return getPrefs()?.getFloat(key, default) ?: default
    }
    
    fun putLong(key: String, value: Long) {
        getPrefs()?.edit()?.putLong(key, value)?.apply()
    }
    
    fun getLong(key: String, default: Long = 0L): Long {
        return getPrefs()?.getLong(key, default) ?: default
    }
    
    fun remove(key: String) {
        getPrefs()?.edit()?.remove(key)?.apply()
    }
    
    fun clear() {
        getPrefs()?.edit()?.clear()?.apply()
    }
}
