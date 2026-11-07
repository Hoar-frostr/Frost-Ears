package com.frost.ears.ui.navigation

enum class Route(
    val title: String,
    val navLabel: String,
) {
    Home(title = "Frost Ears", navLabel = "主页"),
    About(title = "关于", navLabel = "关于"),
    ;

    companion object {
        val all: List<Route> = entries

        fun of(index: Int): Route = all[index.coerceIn(0, all.lastIndex)]

        fun indexOf(route: Route): Int = route.ordinal
    }
}
