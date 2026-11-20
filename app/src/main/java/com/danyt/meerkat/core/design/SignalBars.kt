package com.danyt.meerkat.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private const val BAR_COUNT = 4

/**
 * Signal strength as four rising bars, the first [level] of them filled. Level 0 shows all bars empty.
 * Decorative: say the strength in text next to it for screen readers.
 */
@Composable
fun SignalBars(level: Int, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Canvas(modifier.size(width = 17.dp, height = 14.dp)) {
        val gap = 1.5.dp.toPx()
        val barWidth = (size.width - gap * (BAR_COUNT - 1)) / BAR_COUNT
        val radius = CornerRadius(1.dp.toPx())
        for (index in 0 until BAR_COUNT) {
            val height = size.height * (index + 1) / BAR_COUNT
            drawRoundRect(
                color = if (index < level) colors.accent else colors.tertiaryLabel.copy(alpha = 0.35f),
                topLeft = Offset(index * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
}

@Preview
@Composable
private fun SignalBarsPreview() {
    AppTheme {
        Row(
            Modifier.background(AppTheme.colors.background).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            (0..4).forEach { SignalBars(it) }
        }
    }
}
