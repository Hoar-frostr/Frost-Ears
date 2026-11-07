package com.frost.ears.ui.navigation

import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec

class Navigator(
    private val pagerState: PagerState,
    private val scope: CoroutineScope,
) {

    val current: Route
        get() = Route.of(pagerState.currentPage)

    val currentIndex: Int
        get() = pagerState.currentPage

    fun select(route: Route) {
        val target = Route.indexOf(route)
        if (pagerState.currentPage == target) return
        scope.launch {
            pagerState.animateScrollToPage(
                page = target,
                animationSpec = PagerNavigationSpringSpec,
            )
        }
    }
}
