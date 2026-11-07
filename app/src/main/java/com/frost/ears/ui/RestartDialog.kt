package com.frost.ears.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frost.ears.utils.RootUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun RestartDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onRestart: (label: String, packages: List<String>) -> Unit,
) {
    OverlayDialog(
        show = show,
        title = "重启作用域",
        onDismissRequest = onDismiss,
    ) {
        Column {
            Card {
                ArrowPreference(
                    title = "系统界面",
                    summary = "重启 com.android.systemui",
                    onClick = {
                        onDismiss()
                        onRestart("系统界面", RootUtils.SYSTEM_UI_PROCESSES)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "蓝牙相关进程",
                    summary = "蓝牙 / 魅族耳机 / 魅族 IoT",
                    onClick = {
                        onDismiss()
                        onRestart("蓝牙相关进程", RootUtils.BLUETOOTH_PROCESSES)
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
