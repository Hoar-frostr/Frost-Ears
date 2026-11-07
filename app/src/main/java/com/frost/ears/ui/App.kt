package com.frost.ears.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.frost.ears.utils.App
import com.frost.ears.utils.RemotePrefs
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

internal const val KEY_SPOOF_ENABLED = "tws_device_spoof_enabled"

@Composable
fun FrostEarsApp() {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var serviceReady by remember { mutableStateOf(App.mService != null) }
    var spoofEnabled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = object : App.ServiceStateListener {
            override fun onServiceStateChanged(service: XposedService?) {
                serviceReady = service != null
            }
        }
        App.addServiceStateListener(listener, true)
        onDispose { App.removeServiceStateListener(listener) }
    }

    LaunchedEffect(serviceReady) {
        spoofEnabled = RemotePrefs.getBoolean(KEY_SPOOF_ENABLED, false)
    }

    AppContent(
        snackbarHostState = snackbarHostState,
        serviceReady = serviceReady,
        spoofEnabled = spoofEnabled,
        onSpoofChange = { checked ->
            spoofEnabled = checked
            RemotePrefs.putBoolean(KEY_SPOOF_ENABLED, checked)
        },
        showSnackbar = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        },
    )
}
