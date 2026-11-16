package com.danyt.meerkat.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A small capsule label, for "Soon" markers and counts.
 *
 * @param emphasized fill with the accent color instead of a thin outline, for counts that need attention.
 */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val colors = AppTheme.colors
    Text(
        text = text,
        style = AppTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = if (emphasized) colors.onAccent else colors.secondaryLabel,
        maxLines = 1,
        modifier = modifier
            .background(if (emphasized) colors.accent else Color.Transparent, CircleShape)
            .border(1.dp, if (emphasized) Color.Transparent else colors.outline, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Preview
@Composable
private fun BadgePreview() {
    AppTheme {
        Row(
            modifier = Modifier.background(AppTheme.colors.background).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Badge("Soon")
            Badge("12", emphasized = true)
        }
    }
}
