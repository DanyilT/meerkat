package com.danyt.meerkat.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Filled button in the accent color, for the main action on a screen or sheet. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = AppTheme.colors
    BaseButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        background = if (destructive) colors.destructive else colors.accent,
        contentColor = if (destructive) Color.White else colors.onAccent,
    )
}

/** Outlined button with accent text, for secondary actions. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = AppTheme.colors
    BaseButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        background = Color.Transparent,
        contentColor = if (destructive) colors.destructive else colors.accent,
        outline = colors.outline,
    )
}

/** Plain text button in the accent color, for low-emphasis actions and bar items. */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    emphasized: Boolean = false,
) {
    val colors = AppTheme.colors
    BaseButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        background = Color.Transparent,
        contentColor = if (destructive) colors.destructive else colors.accent,
        minHeight = 44.dp,
        horizontalPadding = 12.dp,
        textStyle = if (emphasized) AppTheme.typography.headline else AppTheme.typography.body,
    )
}

/** Round icon-only button in the accent color, sized for a 44dp touch target. Used in the top bar. */
@Composable
fun IconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = AppTheme.colors.accent,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun BaseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    background: Color,
    contentColor: Color,
    minHeight: Dp = 48.dp,
    horizontalPadding: Dp = 22.dp,
    textStyle: TextStyle = AppTheme.typography.headline,
    outline: Color = Color.Transparent,
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, outline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = minHeight)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = textStyle, color = contentColor, maxLines = 1)
    }
}

@Preview
@Composable
private fun ButtonsPreview() {
    AppTheme {
        Column(
            modifier = Modifier.background(AppTheme.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton("Start Scan", onClick = {}, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Delete", onClick = {}, modifier = Modifier.fillMaxWidth(), destructive = true)
            SecondaryButton("Cancel", onClick = {}, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Disabled", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
            TextButton("Learn More", onClick = {})
            IconButton(AppIcons.Settings, contentDescription = "Settings", onClick = {})
        }
    }
}
