package com.danyt.meerkat.core.privacy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.ConfirmDialog

/**
 * Explains that scanning shows this phone's MAC address to the network, and how to keep it random.
 *
 * The app cannot read or change the MAC address itself, so all it can do is point at the system settings.
 *
 * @param onDismiss the user closed the tip for now (back or tapping outside).
 * @param onDontShowAgain the user asked not to see the tip again. Null hides that choice, for when the tip was
 *   opened on purpose from Settings.
 * @param onOpenedSettings the user left for the system Wi-Fi settings. By default this counts as closing the tip.
 */
@Composable
fun MacPrivacyDialog(
    onDismiss: () -> Unit,
    onDontShowAgain: (() -> Unit)? = null,
    onOpenedSettings: () -> Unit = onDismiss,
) {
    val context = LocalContext.current
    ConfirmDialog(
        title = stringResource(R.string.mac_tip_title),
        message = stringResource(R.string.mac_tip_message),
        confirmLabel = stringResource(R.string.mac_tip_open_wifi),
        dismissLabel = stringResource(
            if (onDontShowAgain != null) R.string.mac_tip_dont_show else R.string.action_close,
        ),
        onConfirm = {
            onOpenedSettings()
            openWifiSettings(context)
        },
        onSecondary = onDontShowAgain ?: onDismiss,
        onDismiss = onDismiss,
    )
}

private fun openWifiSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // A device without a Wi-Fi settings screen. Nothing to open.
    }
}
