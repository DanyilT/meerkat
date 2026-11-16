package com.danyt.meerkat.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danyt.meerkat.R

private val CardShape = RoundedCornerShape(28.dp)

/**
 * A prompt that asks the user to confirm an action, or simply informs them.
 *
 * It is a rounded card that floats above the bottom edge of the screen, within thumb reach: left-aligned
 * title and message, then the buttons side by side with the confirming one filled.
 *
 * @param message optional detail text below the title.
 * @param confirmLabel label of the filled, confirming button on the right.
 * @param dismissLabel label of the left button, or null for a single-button prompt.
 * @param destructive draws the confirm button in red.
 * @param onConfirm called when the confirm button is tapped. The caller closes the dialog.
 * @param onDismiss called when the dialog is cancelled by back or by tapping outside the card.
 * @param onSecondary called when the left button is tapped. Defaults to [onDismiss], making it a cancel button.
 * Pass something else for a second real choice, in which case only back or tapping outside cancels.
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    dismissLabel: String? = stringResource(R.string.action_cancel),
    destructive: Boolean = false,
    onSecondary: () -> Unit = onDismiss,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ConfirmDialogCard(title, message, confirmLabel, dismissLabel, destructive, onConfirm, onSecondary)
        }
    }
}

@Composable
private fun ConfirmDialogCard(
    title: String,
    message: String?,
    confirmLabel: String,
    dismissLabel: String?,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onSecondary: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (colors.isDark) colors.glassFallback else colors.elevated)
            .border(1.dp, colors.outline, CardShape)
            // Swallow taps on the card so they do not count as "outside".
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(20.dp),
    ) {
        Text(
            text = title,
            style = AppTheme.typography.title,
            color = colors.label,
            modifier = Modifier.semantics { heading() },
        )
        if (message != null) {
            Text(
                text = message,
                style = AppTheme.typography.body,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (dismissLabel != null) {
                SecondaryButton(dismissLabel, onClick = onSecondary, modifier = Modifier.weight(1f))
            }
            PrimaryButton(confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f), destructive = destructive)
        }
    }
}

@Preview
@Composable
private fun ConfirmDialogPreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.background).padding(12.dp)) {
            ConfirmDialogCard(
                title = "Clear Cache?",
                message = "This removes 12.4 MB of temporary files.",
                confirmLabel = "Clear",
                dismissLabel = "Cancel",
                destructive = true,
                onConfirm = {},
                onSecondary = {},
            )
        }
    }
}
