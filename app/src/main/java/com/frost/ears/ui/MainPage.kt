package com.frost.ears.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.frost.ears.utils.RootUtils
import com.frost.ears.utils.IconProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.frost.ears.ui.utils.AdaptiveTopAppBar
import com.frost.ears.ui.utils.BlurredBar
import com.frost.ears.ui.utils.pageContentPadding
import com.frost.ears.ui.utils.pageScrollModifiers
import com.frost.ears.ui.utils.rememberBlurBackdrop
import com.frost.ears.ui.utils.shouldShowSplitPane

@Composable
fun MainPage(
    outerPadding: PaddingValues,
    serviceReady: Boolean,
    spoofEnabled: Boolean,
    onSpoofChange: (Boolean) -> Unit,
    showSnackbar: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val isWideScreen = shouldShowSplitPane()

    var iconExpanded by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var rootDenied by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                blurEnabled = blurActive,
                blurRadius = 16f,
                surfaceAlpha = 0.45f,
            ) {
                AdaptiveTopAppBar(
                    title = "Frost Ears",
                    showTopAppBar = true,
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    actions = {
                        IconButton(
                            onClick = {
                                if (rootDenied) {
                                    showSnackbar("需要 root 权限才能重启作用域")
                                } else {
                                    showRestartDialog = true
                                }
                            },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "重启作用域",
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = pageContentPadding(
            innerPadding = innerPadding,
            outerPadding = outerPadding,
            isWideScreen = isWideScreen,
            extraTop = 8.dp,
            extraBottom = 24.dp,
        )
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .pageScrollModifiers(
                            enableScrollEndHaptic = true,
                            showTopAppBar = true,
                            topAppBarScrollBehavior = topAppBarScrollBehavior,
                        ),
                    contentPadding = contentPadding,
                ) {
                    item(key = "main") {
                        Card(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background),
                        ) {
                            ConnectionStatus(serviceReady = serviceReady)
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SwitchPreference(
                                title = "耳机伪装",
                                summary = if (serviceReady) {
                                    "把第三方耳机伪装成魅族官方设备"
                                } else {
                                    "需要 LSPosed 服务连接后才能修改"
                                },
                                checked = spoofEnabled,
                                onCheckedChange = onSpoofChange,
                                enabled = serviceReady,
                            )
                        }
                    }

                    item(key = "brands-title") {
                        SmallTitle("支持品牌")
                    }
                    item(key = "brands") {
                        Card(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background),
                        ) {
                            BasicComponent(
                                title = "VIVO／iQOO",
                                summary = "vivo TWS 4 通过测试",
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            BasicComponent(
                                title = "原道",
                                summary = "YUANDAO OriG in 通过测试",
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            BasicComponent(
                                title = "HUAWEI",
                                summary = "FreeBuds 7i 通过测试",
                            )
                        }
                    }

                    item(key = "icon-title") {
                        SmallTitle("自定义")
                    }
                    item(key = "icon") {
                        CustomIconCard(
                            expanded = iconExpanded,
                            onToggle = { iconExpanded = !iconExpanded },
                        )
                    }
                }

                RestartDialog(
                    show = showRestartDialog,
                    onDismiss = { showRestartDialog = false },
                    onRestart = { label, packages ->
                        showRestartDialog = false
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                RootUtils.restartWithVibration(context.applicationContext, packages)
                            }
                            if (ok) {
                                showSnackbar("已重启：$label")
                            } else {
                                rootDenied = true
                                showSnackbar("需要 root 权限才能重启作用域")
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatus(serviceReady: Boolean) {
    val dotColor = if (serviceReady) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.error
    }
    BasicComponent(
        title = if (serviceReady) "LSPosed 已连接" else "LSPosed 未连接",
        summary = if (serviceReady) {
            "模块已激活，设置可正常下发"
        } else {
            "请在 LSPosed 中启用本模块"
        },
        startAction = {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        },
    )
}

@Composable
private fun CustomIconCard(expanded: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "customIconArrow",
    )
    var iconVersion by remember { mutableIntStateOf(0) }

    val pickBox = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) saveIcon(context, scope, uri, IconProvider.ALIAS_BOX) { iconVersion++ }
    }
    val pickLeft = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) saveIcon(context, scope, uri, IconProvider.ALIAS_LEFT) { iconVersion++ }
    }
    val pickRight = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) saveIcon(context, scope, uri, IconProvider.ALIAS_RIGHT) { iconVersion++ }
    }

    val hasBox = remember(iconVersion) { iconFile(context, IconProvider.ALIAS_BOX).exists() }
    val hasLeft = remember(iconVersion) { iconFile(context, IconProvider.ALIAS_LEFT).exists() }
    val hasRight = remember(iconVersion) { iconFile(context, IconProvider.ALIAS_RIGHT).exists() }

    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background),
    ) {
        BasicComponent(
            title = "自定义耳机图标",
            summary = "从相册选择耳机仓 / 左耳 / 右耳图片",
            holdDownState = expanded,
            onClick = onToggle,
            endActions = {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(width = 10.dp, height = 16.dp)
                        .graphicsLayer { rotationZ = rotation },
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 10.dp),
            ) {
                PickIconRow(
                    title = "耳机仓",
                    picked = hasBox,
                    onPick = {
                        pickBox.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
                PickIconRow(
                    title = "左耳",
                    picked = hasLeft,
                    onPick = {
                        pickLeft.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
                PickIconRow(
                    title = "右耳",
                    picked = hasRight,
                    onPick = {
                        pickRight.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PickIconRow(title: String, picked: Boolean, onPick: () -> Unit) {
    ArrowPreference(
        title = title,
        summary = if (picked) "已选择，点击更换" else "未选择，点击从相册选择",
        onClick = onPick,
    )
}

private fun iconFile(context: Context, name: String): File =
    File(File(context.filesDir, IconProvider.DIR_NAME), "$name.png")

private fun saveIcon(
    context: Context,
    scope: CoroutineScope,
    uri: Uri,
    name: String,
    onDone: () -> Unit,
) {
    scope.launch {
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, IconProvider.DIR_NAME).apply { mkdirs() }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, "$name.png").outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        onDone()
    }
}
