package com.danyt.meerkat.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
 * Centered placeholder for a screen or list with nothing to show.
 *
 * @param icon optional large icon above the title.
 * @param message optional supporting line below the title.
 * @param action optional slot for a button below the text.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.tertiaryLabel, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = AppTheme.typography.title, color = colors.label, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = AppTheme.typography.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * Indeterminate spinner for work in progress.
 *
 * @param label optional text below the spinner, for example "Scanning".
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier, label: String? = null) {
    val colors = AppTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = colors.secondaryLabel,
            trackColor = colors.fill,
            strokeWidth = 3.dp,
        )
        if (label != null) {
            Spacer(Modifier.height(10.dp))
            Text(label, style = AppTheme.typography.subhead, color = colors.secondaryLabel)
        }
    }
}

@Preview
@Composable
private fun EmptyStatePreview() {
    AppTheme {
        Column(Modifier.background(AppTheme.colors.background), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                title = "No Devices",
                icon = AppIcons.Lan,
                message = "Run a scan to find devices on your network.",
                action = { PrimaryButton("Start Scan", onClick = {}) },
            )
            LoadingIndicator(Modifier.padding(24.dp), label = "Scanning")
        }
    }
}
