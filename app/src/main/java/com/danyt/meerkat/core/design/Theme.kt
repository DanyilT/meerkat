package com.danyt.meerkat.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The default accent, a violet. Used in previews and before settings load. */
val DefaultAccent = Color(0xFF7C5CFC)

private const val THEME_FADE_MILLIS = 320

private val LocalAppColors = staticCompositionLocalOf { appColors(dark = false, accent = DefaultAccent) }
private val LocalAppTypography = staticCompositionLocalOf { AppTypography() }
private val LocalAnimationsEnabled = staticCompositionLocalOf { true }
private val LocalBlurEnabled = staticCompositionLocalOf { true }

/** Accessors for the current theme values. Use inside [AppTheme]. */
object AppTheme {
    /** Current semantic colors. */
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    /** Current type scale. */
    val typography: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current

    /** False when the user turned animations off. Components and navigation should skip motion. */
    val animationsEnabled: Boolean
        @Composable @ReadOnlyComposable get() = LocalAnimationsEnabled.current

    /** False when the user turned blur effects off. Glass surfaces then use their solid translucent fill. */
    val blurEnabled: Boolean
        @Composable @ReadOnlyComposable get() = LocalBlurEnabled.current
}

/**
 * Applies the app theme to [content]. Can be nested, for example to preview a different accent.
 *
 * Material3 is themed from the same palette so its base components match, but dynamic color is never used.
 *
 * @param darkTheme use the dark palette.
 * @param accent accent color for buttons, switches, selection, and links.
 * @param monochrome replace the accent with black (light) or white (dark).
 * @param animationsEnabled whether components and navigation may animate.
 * @param blurEnabled whether glass surfaces and transitions may blur.
 * @param fadeColorChanges cross-fade every color when the palette changes, for example light to dark. Leave
 * off for nested themes that must follow a value immediately.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    accent: Color = DefaultAccent,
    monochrome: Boolean = false,
    animationsEnabled: Boolean = true,
    blurEnabled: Boolean = true,
    fadeColorChanges: Boolean = false,
    content: @Composable () -> Unit,
) {
    val target = remember(darkTheme, accent, monochrome) { appColors(darkTheme, accent, monochrome) }
    val colors = if (fadeColorChanges && animationsEnabled) target.faded() else target
    val typography = remember { AppTypography() }
    val scheme = remember(colors) {
        if (colors.isDark) {
            darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                background = colors.background,
                onBackground = colors.label,
                surface = colors.background,
                onSurface = colors.label,
                error = colors.destructive,
            )
        } else {
            lightColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                background = colors.background,
                onBackground = colors.label,
                surface = colors.background,
                onSurface = colors.label,
                error = colors.destructive,
            )
        }
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalAppTypography provides typography,
            LocalAnimationsEnabled provides animationsEnabled,
            LocalBlurEnabled provides blurEnabled,
            LocalContentColor provides colors.label,
            LocalTextStyle provides typography.body,
            content = content,
        )
    }
}

/** Returns these colors, each animating toward its new value whenever the palette changes. */
@Composable
private fun AppColors.faded(): AppColors {
    val spec = tween<Color>(THEME_FADE_MILLIS)

    @Composable
    fun fade(color: Color): Color = animateColorAsState(color, spec, label = "theme").value

    return copy(
        background = fade(background),
        card = fade(card),
        elevated = fade(elevated),
        label = fade(label),
        secondaryLabel = fade(secondaryLabel),
        tertiaryLabel = fade(tertiaryLabel),
        separator = fade(separator),
        outline = fade(outline),
        fill = fade(fill),
        accent = fade(accent),
        onAccent = fade(onAccent),
        destructive = fade(destructive),
        glassTint = fade(glassTint),
        glassFallback = fade(glassFallback),
    )
}
