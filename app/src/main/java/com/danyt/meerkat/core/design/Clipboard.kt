package com.danyt.meerkat.core.design

import android.os.PersistableBundle
import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

/**
 * Returns a function that copies text to the clipboard, with a short vibration as feedback. Meant for long
 * presses on values. Android 13 and newer also show their own "copied" notice.
 *
 * @param sensitive marks the text as secret, such as a password, so Android 13 and newer hide it in the
 *   "copied" notice and keyboard suggestions.
 */
@Composable
fun rememberCopyText(sensitive: Boolean = false): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return { text ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val clip = ClipData.newPlainText("Meerkat", text)
        if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        scope.launch { clipboard.setClipEntry(ClipEntry(clip)) }
    }
}

/** ClipDescription.EXTRA_IS_SENSITIVE, written out so it also compiles against older platforms. */
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
