package com.danyt.meerkat.core.permission

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** Android 17 (API 37) put access to devices on the local network behind this runtime permission. */
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val FIRST_API_WITH_PERMISSION = 37

/** True when the app may talk to devices on the local network. Always true before Android 17. */
fun hasLocalNetworkAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT < FIRST_API_WITH_PERMISSION ||
        context.checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/**
 * Returns a function to call right before a scan of the local network.
 *
 * When access is already there (or the Android version does not need it), [onResult] gets true at once.
 * Otherwise the system prompt is shown and [onResult] gets the user's answer. Android stops showing the prompt
 * after repeated refusals; the answer is then false straight away, and the caller should point the user to
 * [openAppSettings].
 */
@Composable
fun rememberLocalNetworkRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted)
    }
    return {
        if (hasLocalNetworkAccess(context)) currentOnResult(true) else launcher.launch(ACCESS_LOCAL_NETWORK)
    }
}

/** Opens this app's page in system Settings, where the user can change its permissions. */
fun openAppSettings(context: Context) {
    val app = Uri.fromParts("package", context.packageName, null)
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/**
 * Opens a system settings screen, such as [Settings.ACTION_BLUETOOTH_SETTINGS]. Falls back to the main settings
 * screen on the rare device that does not have it.
 */
fun openSystemSettings(context: Context, action: String) {
    for (intent in listOf(Intent(action), Intent(Settings.ACTION_SETTINGS))) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        }
    }
}
