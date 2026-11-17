package com.danyt.meerkat.feature.hotspot

import com.danyt.meerkat.core.design.qrBackground
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.animation.togetherWith
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import com.danyt.meerkat.core.qr.wifiQrText
import com.danyt.meerkat.core.design.QrImage
import com.danyt.meerkat.core.design.InfoDialog
import androidx.compose.ui.Alignment
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.ConfirmDialog
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.IconButton
import com.danyt.meerkat.core.design.InfoHeading
import com.danyt.meerkat.core.design.InfoText
import com.danyt.meerkat.core.design.LoadingIndicator
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.PrimaryButton
import com.danyt.meerkat.core.design.SecondaryButton
import com.danyt.meerkat.core.design.rememberCopyText
import com.danyt.meerkat.core.help.IntroDialog
import com.danyt.meerkat.core.help.IntroKeys
import com.danyt.meerkat.core.help.Intros
import com.danyt.meerkat.core.help.rememberIntro
import com.danyt.meerkat.core.net.HotspotFailure
import com.danyt.meerkat.core.net.HotspotState
import com.danyt.meerkat.core.net.LocalHotspot
import com.danyt.meerkat.core.permission.openAppSettings
import com.danyt.meerkat.core.permission.rememberPermissionRequest
import kotlinx.coroutines.delay

/**
 * The hotspot tool, within what Android allows without root: see whether a hotspot is on, start a
 * local-only one, open the system hotspot settings, and scan the devices that joined.
 */
@Composable
fun HotspotScreen(
    viewModel: HotspotViewModel,
    intros: Intros,
    onBack: () -> Unit,
    onScan: (network: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val intro = rememberIntro(intros, IntroKeys.HOTSPOT)
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }
    val requestNearby = rememberPermissionRequest(LocalHotspot.NEARBY_WIFI_DEVICES) { granted ->
        if (granted) viewModel.startLocal() else permissionDenied = true
    }

    // The hotspot can be switched on or off from the quick settings at any time, so look again every few
    // seconds while the screen is in front. This only reads the phone's own network interfaces.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refresh()
                delay(REFRESH_MILLIS)
            }
        }
    }

    HotspotContent(
        state = state,
        onBack = onBack,
        onInfo = intro.open.takeIf { intro.enabled },
        onScan = onScan,
        onOpenSettings = { openHotspotSettings(context) },
        onStartLocal = requestNearby,
        onStopLocal = viewModel::stopLocal,
        onShowQr = { showQr = true },
    )

    val local = state.local
    if (showQr && local is HotspotState.On && local.ssid != null) {
        // The card takes the code's own background, so the code sits on one plain surface.
        InfoDialog(
            title = stringResource(R.string.hotspot_qr_title),
            onDismiss = { showQr = false },
            containerColor = qrBackground(),
        ) {
            QrImage(
                text = wifiQrText(local.ssid, local.passphrase, local.security),
                description = stringResource(R.string.hotspot_qr_description, local.ssid),
                modifier = Modifier.fillMaxWidth(),
            )
            InfoText(stringResource(R.string.hotspot_qr_hint))
        }
    }

    IntroDialog(intro, stringResource(R.string.feature_hotspot_title)) {
        InfoText(stringResource(R.string.intro_hotspot_what))
        InfoHeading(stringResource(R.string.intro_features))
        InfoText(stringResource(R.string.intro_hotspot_features))
        InfoHeading(stringResource(R.string.intro_hotspot_limits_heading))
        InfoText(stringResource(R.string.hotspot_limits))
    }
    if (permissionDenied) {
        ConfirmDialog(
            title = stringResource(R.string.hotspot_permission_title),
            message = stringResource(R.string.hotspot_permission_message),
            confirmLabel = stringResource(R.string.lan_access_open_settings),
            onConfirm = {
                permissionDenied = false
                openAppSettings(context)
            },
            onDismiss = { permissionDenied = false },
        )
    }
}

@Composable
private fun HotspotContent(
    state: HotspotUiState,
    onBack: () -> Unit,
    onInfo: (() -> Unit)?,
    onScan: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onStartLocal: () -> Unit,
    onStopLocal: () -> Unit,
    onShowQr: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val copy = rememberCopyText()
    val animate = AppTheme.animationsEnabled
    AppScaffold(
        title = stringResource(R.string.feature_hotspot_title),
        onTitleClick = onBack,
        actions = { onInfo?.let { IconButton(AppIcons.Info, stringResource(R.string.intro_button), it) } },
    ) {
        item(key = "status") {
            GroupedSection(
                header = stringResource(R.string.hotspot_status),
                footer = stringResource(R.string.hotspot_status_footer),
            ) {
                val network = state.network
                GroupedRow(
                    title = stringResource(R.string.feature_hotspot_title),
                    trailing = {
                        // On and Off swap with a short cross-fade.
                        AnimatedContent(
                            targetState = network != null,
                            transitionSpec = { fade(animate) },
                            label = "hotspotOn",
                        ) { on ->
                            Text(
                                text = stringResource(if (on) R.string.hotspot_on else R.string.hotspot_off),
                                style = AppTheme.typography.body,
                                color = colors.secondaryLabel,
                            )
                        }
                    },
                )
                // The network rows unfold when a hotspot appears and fold away when it goes.
                AnimatedVisibility(
                    visible = network != null,
                    enter = unfold(animate),
                    exit = fold(animate),
                ) {
                    // Keeps showing the last values while folding away. Updated after composition, not during it.
                    val shown = remember { mutableStateOf(state) }
                    SideEffect { if (network != null) shown.value = state }
                    val rows = if (network != null) state else shown.value
                    Column {
                        rows.network?.let { value ->
                            val label = stringResource(R.string.hotspot_network)
                            GroupedRow(label, value = value, onLongClick = { copy(value) })
                        }
                        rows.ownAddress?.let { own ->
                            val label = stringResource(R.string.hotspot_own_address)
                            GroupedRow(label, value = own, onLongClick = { copy(own) })
                        }
                    }
                }
            }
        }
        item(key = "actions") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryButton(
                    text = stringResource(R.string.hotspot_scan),
                    onClick = { state.network?.let(onScan) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.network != null,
                )
                SecondaryButton(stringResource(R.string.hotspot_open_settings), onOpenSettings, Modifier.fillMaxWidth())
            }
        }
        item(key = "local") {
            GroupedSection(
                header = stringResource(R.string.hotspot_local),
                footer = stringResource(
                    if (state.localSupported) R.string.hotspot_local_footer else R.string.hotspot_local_unsupported,
                ),
            ) {
                // Starting, running, and stopped each have their own rows; they cross-fade and the card resizes.
                AnimatedContent(
                    targetState = state.local,
                    contentKey = { it::class },
                    transitionSpec = { fade(animate).using(SizeTransform(clip = false)) },
                    label = "localHotspot",
                ) { local ->
                    Column { LocalHotspotRows(local, state.localSupported, onStartLocal, onStopLocal, onShowQr) }
                }
            }
        }
        (state.local as? HotspotState.Failed)?.let { failed ->
            item(key = "failed") {
                val spacing = Modifier.padding(horizontal = 16.dp).padding(bottom = 22.dp)
                Note(stringResource(failed.reason.message), spacing.animated(this, animate))
            }
        }
        item(key = "limits") {
            Note(stringResource(R.string.hotspot_limits), Modifier.padding(horizontal = 16.dp).animated(this, animate))
        }
    }
}

/** The rows of the local-only hotspot card for one [local] state. */
@Composable
private fun LocalHotspotRows(
    local: HotspotState,
    supported: Boolean,
    onStartLocal: () -> Unit,
    onStopLocal: () -> Unit,
    onShowQr: () -> Unit,
) {
    val colors = AppTheme.colors
    val copy = rememberCopyText()
    val copySecret = rememberCopyText(sensitive = true)
    when (local) {
        HotspotState.Starting -> GroupedRow(
            title = stringResource(R.string.hotspot_local_starting),
            trailing = { LoadingIndicator() },
        )
        is HotspotState.On -> {
            local.ssid?.let { ssid ->
                val label = stringResource(R.string.hotspot_ssid)
                GroupedRow(label, value = ssid, onLongClick = { copy(ssid) })
            }
            local.passphrase?.let { pass ->
                val label = stringResource(R.string.hotspot_password)
                GroupedRow(label, value = pass, onLongClick = { copySecret(pass) })
            }
            if (local.ssid != null) {
                GroupedRow(
                    title = stringResource(R.string.hotspot_qr_show),
                    titleColor = colors.accent,
                    chevron = true,
                    onClick = onShowQr,
                )
            }
            GroupedRow(
                title = stringResource(R.string.hotspot_local_stop),
                titleColor = colors.destructive,
                onClick = onStopLocal,
            )
        }
        else -> GroupedRow(
            title = stringResource(R.string.hotspot_local_start),
            titleColor = colors.accent,
            enabled = supported,
            onClick = onStartLocal,
        )
    }
}

private fun fade(animate: Boolean) = if (animate) {
    fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120))
} else {
    EnterTransition.None togetherWith ExitTransition.None
}

private fun unfold(animate: Boolean) =
    if (animate) expandVertically(tween(240)) + fadeIn(tween(240)) else EnterTransition.None

private fun fold(animate: Boolean) =
    if (animate) shrinkVertically(tween(220)) + fadeOut(tween(160)) else ExitTransition.None

/** Animates a list item appearing, disappearing, and moving, unless animations are turned off. */
private fun Modifier.animated(scope: LazyItemScope, enabled: Boolean): Modifier =
    if (enabled) with(scope) { animateItem() } else this

private val HotspotFailure.message: Int
    get() = when (this) {
        HotspotFailure.Unsupported -> R.string.hotspot_local_unsupported
        HotspotFailure.NoPermission -> R.string.hotspot_permission_message
        HotspotFailure.IncompatibleMode -> R.string.hotspot_failed_mode
        HotspotFailure.NoChannel -> R.string.hotspot_failed_channel
        HotspotFailure.Disallowed -> R.string.hotspot_failed_disallowed
        HotspotFailure.Other -> R.string.hotspot_failed_other
    }

/**
 * Opens the system screen with the hotspot switch. Android has no public shortcut for it, so the common one is
 * tried first, then the general network settings.
 */
private fun openHotspotSettings(context: Context) {
    val intents = listOf(Intent("android.settings.TETHER_SETTINGS"), Intent(Settings.ACTION_WIRELESS_SETTINGS))
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        }
    }
}

private const val REFRESH_MILLIS = 3_000L

@Preview
@Composable
private fun HotspotScreenPreview() {
    AppTheme {
        HotspotContent(
            state = HotspotUiState(
                network = "192.168.49.0/24",
                ownAddress = "192.168.49.1",
                local = HotspotState.On("DIRECT-meerkat", "k3v9x2pq"),
                localSupported = true,
            ),
            onBack = {},
            onInfo = {},
            onScan = {},
            onOpenSettings = {},
            onStartLocal = {},
            onStopLocal = {},
        )
    }
}
