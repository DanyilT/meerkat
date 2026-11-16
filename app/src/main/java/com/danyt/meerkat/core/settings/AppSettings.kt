package com.danyt.meerkat.core.settings

/** How the app picks between its light and dark palettes. */
enum class ThemeMode { System, Light, Dark }

/**
 * User preferences that apply to the whole app.
 *
 * @property themeMode light, dark, or follow the system.
 * @property accentColor accent as an opaque ARGB integer.
 * @property monochrome when true the accent is replaced by the label color (black or white).
 * @property animationsEnabled when false, transitions and component animations are skipped.
 * @property blurEnabled when false, glass surfaces and screen transitions are drawn without blur.
 * @property dashboardTiles routes of the tools on the dashboard, in display order, or null if the user never
 * changed them.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val accentColor: Int = DEFAULT_ACCENT_COLOR,
    val monochrome: Boolean = false,
    val animationsEnabled: Boolean = true,
    val blurEnabled: Boolean = true,
    val dashboardTiles: List<String>? = null,
) {
    companion object {
        /** A violet, used until the user picks an accent. */
        const val DEFAULT_ACCENT_COLOR: Int = 0xFF7C5CFC.toInt()
    }
}
