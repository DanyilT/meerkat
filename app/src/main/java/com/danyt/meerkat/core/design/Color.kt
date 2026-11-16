package com.danyt.meerkat.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Semantic colors used by every component. Names follow their role, not their hue.
 *
 * @property background screen background behind grouped content.
 * @property card fill of grouped sections and cards. Slightly translucent.
 * @property elevated fill for things that sit on a card or fill, such as the selected segment.
 * @property label primary text.
 * @property secondaryLabel supporting text, values, and section headers.
 * @property tertiaryLabel chevrons, placeholders, and disabled content.
 * @property separator hairline dividers.
 * @property outline thin border around cards, fields, and outlined controls.
 * @property fill faint neutral fill for notes and pressed or inactive areas.
 * @property accent the user's accent color (or the label color in monochrome).
 * @property onAccent content drawn on top of [accent].
 * @property destructive destructive actions and errors.
 * @property glassTint tint drawn over blurred content on glass surfaces.
 * @property glassFallback glass fill used where blur is unavailable.
 * @property isDark whether this is the dark palette.
 */
@Immutable
data class AppColors(
    val background: Color,
    val card: Color,
    val elevated: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val outline: Color,
    val fill: Color,
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val glassTint: Color,
    val glassFallback: Color,
    val isDark: Boolean,
)

/** Builds the light or dark palette around [accent]. With [monochrome] the accent becomes black or white. */
fun appColors(dark: Boolean, accent: Color, monochrome: Boolean = false): AppColors {
    val label = if (dark) Color(0xFFF5F5F7) else Color(0xFF111114)
    val resolvedAccent = if (monochrome) label else accent
    val onAccent = if (resolvedAccent.luminance() > 0.55f) Color.Black else Color.White
    return if (dark) {
        AppColors(
            background = Color(0xFF0A0A0D),
            card = Color(0x99151519),
            elevated = Color(0xFF2A2A30),
            label = label,
            secondaryLabel = Color(0x99F5F5F7),
            tertiaryLabel = Color(0x4DF5F5F7),
            separator = Color(0x24FFFFFF),
            outline = Color(0x2EFFFFFF),
            fill = Color(0x1FFFFFFF),
            accent = resolvedAccent,
            onAccent = onAccent,
            destructive = Color(0xFFFF6369),
            glassTint = Color(0xB3121216),
            glassFallback = Color(0xF218181C),
            isDark = true,
        )
    } else {
        AppColors(
            background = Color(0xFFF4F4F6),
            card = Color(0xB8FFFFFF),
            elevated = Color(0xFFFFFFFF),
            label = label,
            secondaryLabel = Color(0x99111114),
            tertiaryLabel = Color(0x4D111114),
            separator = Color(0x1F111114),
            outline = Color(0x26111114),
            fill = Color(0x12111114),
            accent = resolvedAccent,
            onAccent = onAccent,
            destructive = Color(0xFFE5484D),
            glassTint = Color(0xB8F6F6F8),
            glassFallback = Color(0xF2F6F6F8),
            isDark = false,
        )
    }
}
