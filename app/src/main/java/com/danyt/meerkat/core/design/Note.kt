package com.danyt.meerkat.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A short inline note, hint, or piece of information: an icon and centered text on a faint translucent
 * background. Use it for guidance that belongs to the screen, not for errors or confirmations.
 *
 * @param icon leading icon, an info mark by default.
 */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, icon: ImageVector = AppIcons.Info) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.fill, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = AppTheme.typography.subhead,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Preview
@Composable
private fun NotePreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
            Note("Drag tiles to rearrange them.")
        }
    }
}
