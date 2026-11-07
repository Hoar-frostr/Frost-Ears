package com.frost.ears.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.frost.ears.ui.navigation.Navigator
import com.frost.ears.ui.navigation.Route
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarData
import top.yukonga.miuix.kmp.basic.SnackbarDefaults
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import com.frost.ears.ui.utils.BlurredBar
import com.frost.ears.ui.utils.rememberBlurBackdrop

@Composable
fun AppContent(
    snackbarHostState: SnackbarHostState,
    serviceReady: Boolean,
    spoofEnabled: Boolean,
    onSpoofChange: (Boolean) -> Unit,
    showSnackbar: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val pagerState = rememberPagerState(pageCount = { Route.all.size })
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = PagerNavigationSpringSpec,
    )
    val navigator = remember(pagerState) { Navigator(pagerState, scope) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.surface,
        snackbarHost = {
            SnackbarHost(state = snackbarHostState) { data: SnackbarData ->
                Snackbar(
                    data = data,
                    colors = SnackbarDefaults.snackbarColors(
                        containerColor = MiuixTheme.colorScheme.surfaceContainer,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                    ),
                )
            }
        },
        bottomBar = {
            BlurredBar(
                backdrop = backdrop,
                blurEnabled = blurActive,
            ) {
                NavigationBar(color = barColor) {
                    Route.all.forEach { route ->
                        NavigationBarItem(
                            selected = navigator.currentIndex == Route.indexOf(route),
                            onClick = { navigator.select(route) },
                            icon = routeIcon(route),
                            label = route.navLabel,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                flingBehavior = flingBehavior,
                userScrollEnabled = false,
            ) { page ->
                when (Route.of(page)) {
                    Route.Home -> MainPage(
                        outerPadding = innerPadding,
                        serviceReady = serviceReady,
                        spoofEnabled = spoofEnabled,
                        onSpoofChange = onSpoofChange,
                        showSnackbar = showSnackbar,
                    )
                    Route.About -> AboutPage(outerPadding = innerPadding)
                }
            }
        }
    }
}

private fun routeIcon(route: Route): ImageVector = when (route) {
    Route.Home -> MiuixIcons.VerticalSplit
    Route.About -> MiuixIcons.Info
}
