package com.danyt.meerkat.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.GlassBackdrop
import com.danyt.meerkat.core.settings.ThemeMode

/** The app's only activity. Applies the saved theme and hosts the navigation graph. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MeerkatApp).container

        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            // Draw nothing until settings are read (a few milliseconds), so the wrong theme never flashes.
            val current = settings ?: return@setContent
            val dark = when (current.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // Keep status and navigation bar icons readable against the app theme, not the system theme.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            AppTheme(
                darkTheme = dark,
                accent = Color(current.accentColor),
                monochrome = current.monochrome,
                animationsEnabled = current.animationsEnabled,
                blurEnabled = current.blurEnabled,
                fadeColorChanges = true,
            ) {
                GlassBackdrop { AppNavHost(container) }
            }
        }
    }
}
