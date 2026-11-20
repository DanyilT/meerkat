package com.danyt.meerkat.core.help

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.core.design.InfoDialog
import com.danyt.meerkat.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Whether introductions are switched on, and which screens have already shown theirs.
 *
 * @property enabled the Settings switch for introductions and their info buttons.
 * @property seen keys of the screens whose introduction the user has acknowledged.
 */
data class IntroState(val enabled: Boolean, val seen: Set<String>)

/**
 * Keeps track of the one-time introductions that explain each screen to a new user.
 *
 * It lives for the whole app, so every screen reads the same state.
 */
class Intros(private val repository: SettingsRepository, private val scope: CoroutineScope) {
    /** The current state, or null until the saved settings have been read. */
    val state: StateFlow<IntroState?> = repository.settings
        .map { IntroState(it.introsEnabled, it.seenIntros) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Records that the introduction with [key] was shown and acknowledged, so it does not open by itself again. */
    fun markSeen(key: String) {
        scope.launch { repository.markIntroSeen(key) }
    }
}

/**
 * One screen's introduction.
 *
 * @property enabled whether to show the info button that reopens the introduction.
 * @property visible whether the introduction is on screen now.
 * @property firstTime true when it opened by itself for a new user.
 */
@Stable
class IntroHandle(
    val enabled: Boolean,
    val visible: Boolean,
    val firstTime: Boolean,
    val open: () -> Unit,
    val close: () -> Unit,
)

/** Connects a screen to its introduction, stored under [key]. */
@Composable
fun rememberIntro(intros: Intros, key: String): IntroHandle {
    val state by intros.state.collectAsStateWithLifecycle()
    var opened by rememberSaveable { mutableStateOf(false) }
    // Set on close, so the dialog goes away at once instead of waiting for the "seen" mark to be saved.
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val firstTime = state?.let { it.enabled && key !in it.seen } == true && !dismissed
    return IntroHandle(
        enabled = state?.enabled == true,
        visible = firstTime || opened,
        firstTime = firstTime,
        open = { opened = true },
        close = {
            opened = false
            if (firstTime) {
                dismissed = true
                intros.markSeen(key)
            }
        },
    )
}

/**
 * Shows a screen's introduction while its [handle] says so. However it is closed, the first showing counts as
 * seen and does not open by itself again.
 */
@Composable
fun IntroDialog(handle: IntroHandle, title: String, content: @Composable ColumnScope.() -> Unit) {
    if (handle.visible) InfoDialog(title, onDismiss = handle.close, content = content)
}

/** Keys under which each screen's introduction is remembered. They are stored, so they must not change. */
object IntroKeys {
    const val HOME = "home"
    const val LAN = "lan"
    const val PORTS = "ports"
    const val REPORTS = "reports"
    const val HOTSPOT = "hotspot"
    const val NEARBY = "nearby"
}
