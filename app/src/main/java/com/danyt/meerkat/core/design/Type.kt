package com.danyt.meerkat.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The app's type scale. All styles use the system sans-serif. */
@Immutable
data class AppTypography(
    val largeTitle: TextStyle = style(34, FontWeight.Bold, lineHeight = 41),
    val title: TextStyle = style(22, FontWeight.Normal, lineHeight = 28),
    val headline: TextStyle = style(17, FontWeight.SemiBold, lineHeight = 22),
    val body: TextStyle = style(17, FontWeight.Normal, lineHeight = 22),
    val subhead: TextStyle = style(15, FontWeight.Normal, lineHeight = 20),
    val caption: TextStyle = style(12, FontWeight.Normal, lineHeight = 16),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight.sp,
)
