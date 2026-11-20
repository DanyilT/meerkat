package com.danyt.meerkat.core.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Returns a function that makes sure [permission] is granted, asking with the system prompt when needed, and
 * then gives [onResult] the answer. When it is already granted, [onResult] gets true at once.
 */
@Composable
fun rememberPermissionRequest(permission: String, onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted)
    }
    return {
        val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        if (granted) currentOnResult(true) else launcher.launch(permission)
    }
}

/**
 * Like [rememberPermissionRequest], for several permissions asked together in one system prompt. [onResult]
 * gets the ones that are granted afterwards.
 */
@Composable
fun rememberPermissionsRequest(
    permissions: List<String>,
    onResult: (granted: Set<String>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        currentOnResult(permissions.filter { context.hasPermission(it) }.toSet())
    }
    return {
        if (permissions.all { context.hasPermission(it) }) {
            currentOnResult(permissions.toSet())
        } else {
            launcher.launch(permissions.toTypedArray())
        }
    }
}

/** Whether the app holds [permission] now. */
fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
