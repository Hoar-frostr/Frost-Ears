package com.frost.ears.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frost.ears.R
import com.frost.ears.ui.component.blend.ColorBlendToken
import com.frost.ears.ui.component.effect.BgEffectBackground
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.frost.ears.ui.utils.BlurredBar
import com.frost.ears.ui.utils.pageContentPadding
import com.frost.ears.ui.utils.pageScrollModifiers
import com.frost.ears.ui.utils.rememberBlurBackdrop
import com.frost.ears.ui.utils.shouldShowSplitPane

private const val QQ_GROUP_URL =
    "https://qun.qq.com/universal-share/share?ac=1&authKey=aqoC6eLQp3Bk%2BRcDvHatXR6Kv2ErISzguk4YxSD3IrtN2cGyqazXF3JoyMg76p0o&busi_data=eyJncm91cENvZGUiOiI3Nzg5ODQ2MjEiLCJ0b2tlbiI6IktXWE52Z05HNEg3L3IzdU5QTGNLcVJiZ1lCekxaM1VUTGhrd0wxeGM5c1lmbis5REZZVTJtK2RFd09XWFphV2kiLCJ1aW4iOiIyNTYxMjMyNzcyIn0%3D&data=yKrisix8Im9Hlz8yN5LLMci9cVrigKq0o_Ztrl63gP3yzb71Mg-mVMEbTgzxqxLY-LjGchyvRw7tN2RCc-A6og&svctype=4&tempid=h5_group_info"

@Composable
fun AboutPage(outerPadding: PaddingValues) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val isWideScreen = shouldShowSplitPane()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    val backdrop = rememberBlurBackdrop()
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(backdrop) {
        derivedStateOf { backdrop != null && scrollProgress == 1f }
    }

    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "-"
    val versionCode = packageInfo?.longVersionCode ?: 0L

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val barColor = if (blurActive) {
                Color.Transparent
            } else {
                if (collapsed) MiuixTheme.colorScheme.surface else Color.Transparent
            }
            val titleColor = MiuixTheme.colorScheme.onSurface.copy(
                alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
            )
            BlurredBar(
                backdrop = backdrop,
                blurEnabled = blurActive,
                blurRadius = 16f,
                surfaceAlpha = 0.45f,
            ) {
                SmallTopAppBar(
                    title = "关于",
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    titleColor = titleColor,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            AboutContent(
                padding = pageContentPadding(
                    innerPadding = innerPadding,
                    outerPadding = outerPadding,
                    isWideScreen = isWideScreen,
                ),
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                lazyListState = lazyListState,
                scrollProgress = scrollProgress,
                onLogoHeightChanged = { logoHeightPx = it },
                versionName = versionName,
                versionCode = versionCode,
            )
        }
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: Float,
    onLogoHeightChanged: (Int) -> Unit,
    versionName: String,
    versionCode: Long,
) {
    val backdrop = rememberBlurBackdrop()
    val uriHandler = LocalUriHandler.current
    val isWideScreen = shouldShowSplitPane()
    val isInDark = isInDarkTheme()
    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }

    val versionCodeProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
    val projectNameProgress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
    val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)

    val logoBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }
    val cardBlend = if (isInDark) {
        ColorBlendToken.Overlay_Thin_Light
    } else {
        ColorBlendToken.Pured_Regular_Light
    }

    val scrollPadding = pageContentPadding(
        padding,
        padding,
        isWideScreen,
        extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
        extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
    )
    val logoPadding = pageContentPadding(
        padding,
        padding,
        isWideScreen,
        extraTop = 40.dp,
        extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
        extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
    )

    BgEffectBackground(
        dynamicBackground = isRuntimeShaderSupported(),
        modifier = Modifier.fillMaxSize(),
        bgModifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        alpha = { 1f - scrollProgress },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = logoPadding.calculateRightPadding(LayoutDirection.Ltr),
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(20.dp)
                        alpha = 1 - iconProgress
                        scaleX = 1 - (iconProgress * 0.05f)
                        scaleY = 1 - (iconProgress * 0.05f)
                    },
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "软件图标",
                    modifier = Modifier
                        .size(96.dp)
                        .then(
                            if (backdrop != null) {
                                Modifier.textureBlur(
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(20.dp),
                                    blurRadius = 150f,
                                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                    colors = BlurDefaults.blurColors(blendColors = logoBlend),
                                    contentBlendMode = BlendMode.DstIn,
                                )
                            } else {
                                Modifier
                            },
                        ),
                    tint = Color.Unspecified,
                )
            }
            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
                    }
                    .then(
                        if (backdrop != null) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 150f,
                                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                colors = BlurDefaults.blurColors(blendColors = logoBlend),
                                contentBlendMode = BlendMode.DstIn,
                            )
                        } else {
                            Modifier
                        },
                    ),
                text = "Frost Ears",
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1 - versionCodeProgress
                        scaleX = 1 - (versionCodeProgress * 0.05f)
                        scaleY = 1 - (versionCodeProgress * 0.05f)
                    },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                text = "v$versionName ($versionCode)",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                ),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateLeftPadding(LayoutDirection.Ltr),
                end = scrollPadding.calculateRightPadding(LayoutDirection.Ltr),
            ),
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp +
                                logoPadding.calculateTopPadding() -
                                scrollPadding.calculateTopPadding() + 126.dp,
                        )
                        .onSizeChanged { size ->
                            onLogoHeightChanged(size.height)
                        },
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "about") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = scrollPadding.calculateBottomPadding()),
                ) {
                    SmallTitle(text = "官方")
                    AboutCard(backdrop = backdrop, cardBlend = cardBlend) {
                        ArrowPreference(
                            title = "Frost Tool",
                            onClick = { uriHandler.openUri("https://github.com/Hoar-frostr/Hoar") },
                        )
                        ArrowPreference(
                            title = "QQ 群聊",
                            onClick = { uriHandler.openUri(QQ_GROUP_URL) },
                        )
                        ArrowPreference(
                            title = "GitHub 开源地址",
                            onClick = { uriHandler.openUri("https://github.com/Hoar-frostr/Frost-Ears") },
                        )
                    }

                    SmallTitle(text = "使用的项目")
                    AboutCard(backdrop = backdrop, cardBlend = cardBlend) {
                        ArrowPreference(
                            title = "HyperEars",
                            onClick = { uriHandler.openUri("https://github.com/silverpoetry/HyperEars") },
                        )
                        ArrowPreference(
                            title = "Miuix",
                            onClick = { uriHandler.openUri("https://github.com/compose-miuix-ui/miuix") },
                        )
                        ArrowPreference(
                            title = "LSPosed",
                            onClick = { uriHandler.openUri("https://github.com/LSPosed/LSPosed") },
                        )
                        ArrowPreference(
                            title = "libxposed",
                            onClick = { uriHandler.openUri("https://github.com/libxposed/api") },
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AboutCard(
    backdrop: LayerBackdrop?,
    cardBlend: List<BlendColorEntry>,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 60f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurDefaults.blurColors(blendColors = cardBlend),
                    )
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.defaultColors(
            if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            Color.Transparent,
        ),
        content = content,
    )
}
