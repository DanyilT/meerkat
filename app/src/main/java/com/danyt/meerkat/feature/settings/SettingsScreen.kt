package com.danyt.meerkat.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.BuildConfig
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.ConfirmDialog
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.GroupedSwitchRow
import com.danyt.meerkat.core.design.LoadingIndicator
import com.danyt.meerkat.core.design.SegmentedControl
import com.danyt.meerkat.core.design.deviceSupportsBlur
import com.danyt.meerkat.core.design.toHexString
import com.danyt.meerkat.core.settings.ThemeMode
import com.danyt.meerkat.core.storage.formatBytes

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAccentSheet by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    // The cache can change while the app is in the background, so measure again on every return.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshCacheSize()
        onPauseOrDispose {}
    }

    SettingsContent(
        state = state,
        onBack = onBack,
        onThemeMode = viewModel::setThemeMode,
        onAccentClick = { showAccentSheet = true },
        onAnimations = viewModel::setAnimationsEnabled,
        onBlur = viewModel::setBlurEnabled,
        onClearCacheClick = { confirmClear = true },
        onOpenCredits = onOpenCredits,
        onOpenLicense = onOpenLicense,
    )

    if (showAccentSheet) {
        AccentColorSheet(
            initialColor = Color(state.settings.accentColor),
            initialMonochrome = state.settings.monochrome,
            onChange = viewModel::setAccent,
            onDismiss = { showAccentSheet = false },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_cache_title),
            message = stringResource(R.string.clear_cache_message, formatBytes(state.cacheSizeBytes ?: 0)),
            confirmLabel = stringResource(R.string.action_clear),
            destructive = true,
            onConfirm = {
                confirmClear = false
                viewModel.clearCache()
            },
            onDismiss = { confirmClear = false },
        )
    }

    state.freedBytes?.let { freed ->
        ConfirmDialog(
            title = stringResource(R.string.cache_cleared_title),
            message = stringResource(R.string.cache_cleared_message, formatBytes(freed)),
            confirmLabel = stringResource(R.string.action_ok),
            dismissLabel = null,
            onConfirm = viewModel::onFreedMessageShown,
            onDismiss = viewModel::onFreedMessageShown,
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccentClick: () -> Unit,
    onAnimations: (Boolean) -> Unit,
    onBlur: (Boolean) -> Unit,
    onClearCacheClick: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    val colors = AppTheme.colors
    val settings = state.settings
    AppScaffold(title = stringResource(R.string.settings), onTitleClick = onBack) {
        item {
            GroupedSection(
                header = stringResource(R.string.settings_appearance),
                footer = stringResource(
                    if (deviceSupportsBlur) {
                        R.string.settings_appearance_footer
                    } else {
                        R.string.settings_appearance_footer_no_blur
                    },
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = AppTheme.typography.body,
                        color = colors.label,
                    )
                    SegmentedControl(
                        options = ThemeMode.entries.map { stringResource(it.label) },
                        selectedIndex = settings.themeMode.ordinal,
                        onSelected = { onThemeMode(ThemeMode.entries[it]) },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                GroupedRow(
                    title = stringResource(R.string.settings_accent),
                    value = if (settings.monochrome) {
                        stringResource(R.string.accent_monochrome)
                    } else {
                        Color(settings.accentColor).toHexString()
                    },
                    chevron = true,
                    onClick = onAccentClick,
                    trailing = { Box(Modifier.size(22.dp).background(colors.accent, CircleShape)) },
                )
                GroupedSwitchRow(
                    title = stringResource(R.string.settings_animations),
                    checked = settings.animationsEnabled,
                    onCheckedChange = onAnimations,
                )
                GroupedSwitchRow(
                    title = stringResource(R.string.settings_blur),
                    checked = settings.blurEnabled && deviceSupportsBlur,
                    onCheckedChange = onBlur,
                    enabled = deviceSupportsBlur,
                )
            }
        }
        item {
            val size = state.cacheSizeBytes
            val canClear = !state.clearingCache && size != null && size > 0
            GroupedSection(
                header = stringResource(R.string.settings_storage),
                footer = stringResource(R.string.settings_storage_footer),
            ) {
                GroupedRow(
                    title = stringResource(R.string.settings_clear_cache),
                    titleColor = colors.accent,
                    value = size?.let(::formatBytes),
                    enabled = canClear,
                    onClick = onClearCacheClick,
                    trailing = if (state.clearingCache || size == null) ({ LoadingIndicator() }) else null,
                )
            }
        }
        item {
            GroupedSection(header = stringResource(R.string.settings_about)) {
                GroupedRow(
                    title = stringResource(R.string.settings_version),
                    value = stringResource(
                        R.string.settings_version_value,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                )
                GroupedRow(title = stringResource(R.string.credits), chevron = true, onClick = onOpenCredits)
                GroupedRow(
                    title = stringResource(R.string.license),
                    value = LICENSE_NAME,
                    chevron = true,
                    onClick = onOpenLicense,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsContent(
            state = SettingsUiState(cacheSizeBytes = 1_300_000),
            onBack = {},
            onThemeMode = {},
            onAccentClick = {},
            onAnimations = {},
            onBlur = {},
            onClearCacheClick = {},
            onOpenCredits = {},
            onOpenLicense = {},
        )
    }
}

/** The name shown for each theme choice. Kept here so the settings model stays free of UI resources. */
private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }
