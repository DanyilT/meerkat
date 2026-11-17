package com.danyt.meerkat.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** A thin pill that fills with the accent color as [progress] goes from 0 to 1. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val target = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = if (AppTheme.animationsEnabled) tween(200) else snap(),
        label = "progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.fill)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(colors.accent, CircleShape))
    }
}

@Preview
@Composable
private fun ProgressBarPreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
            ProgressBar(progress = 0.4f)
        }
    }
}
