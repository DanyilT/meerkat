package com.danyt.meerkat.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.danyt.meerkat.core.design.ConfirmDialog
import com.danyt.meerkat.core.design.LoadingIndicator
import com.danyt.meerkat.core.reports.formatReportDate
import com.danyt.meerkat.core.storage.formatBytes
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.GroupedSwitchRow
import com.danyt.meerkat.core.privacy.MacPrivacyDialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.makers.MakerList
import com.danyt.meerkat.core.makers.MakerListStatus
import com.danyt.meerkat.core.settings.AppSettings
import com.danyt.meerkat.core.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ToolSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val makerList: MakerList,
) : ViewModel() {
    /** The downloaded maker list's state. */
    val makers: StateFlow<MakerListStatus> = makerList.status

    init {
        viewModelScope.launch { makerList.load() }
    }

    /** Downloads the maker list from the IEEE, or updates it. Only ever on the user's request. */
    fun downloadMakers() {
        viewModelScope.launch { makerList.download() }
    }

    fun deleteMakers() {
        viewModelScope.launch { makerList.delete() }
    }

    /** The saved settings, or null until they have been read, so the switches never flash a wrong state. */
    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether the NFC reader reads and shows bank card numbers and expiry dates. */
    fun setShowCardDetails(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowCardDetails(show) }
    }

    /** Sets which tab the tool at [route] opens on; see [SettingsRepository.setStartTab]. */
    fun setStartTab(route: String, tab: String?) {
        viewModelScope.launch { settingsRepository.setStartTab(route, tab) }
    }
}

/**
 * Settings of the tools: the scanners' safety options (authorized ranges, MAC address privacy), then one section
 * per tool with tabs, for the tab it opens on.
 */
@Composable
fun ToolSettingsScreen(
    viewModel: ToolSettingsViewModel,
    tabbedTools: List<TabbedTool>,
    onBack: () -> Unit,
    onOpenScope: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val makers by viewModel.makers.collectAsStateWithLifecycle()
    var showMacTip by rememberSaveable { mutableStateOf(false) }
    ToolSettingsContent(
        settings = settings,
        tabbedTools = tabbedTools,
        onBack = onBack,
        onOpenScope = onOpenScope,
        onMacTipClick = { showMacTip = true },
        onStartTab = viewModel::setStartTab,
        onShowCardDetails = viewModel::setShowCardDetails,
        makers = makers,
        onDownloadMakers = viewModel::downloadMakers,
        onDeleteMakers = viewModel::deleteMakers,
    )
    if (showMacTip) MacPrivacyDialog(onDismiss = { showMacTip = false })
}

@Composable
private fun ToolSettingsContent(
    settings: AppSettings?,
    tabbedTools: List<TabbedTool>,
    onBack: () -> Unit,
    onOpenScope: () -> Unit,
    onMacTipClick: () -> Unit,
    onStartTab: (route: String, tab: String?) -> Unit,
    onShowCardDetails: (Boolean) -> Unit = {},
    makers: MakerListStatus = MakerListStatus(),
    onDownloadMakers: () -> Unit = {},
    onDeleteMakers: () -> Unit = {},
) {
    AppScaffold(title = stringResource(R.string.tool_settings_title), onTitleClick = onBack) {
        if (settings != null) {
            item(key = "scanning") {
                val ranges = settings.authorizedRanges.size
                GroupedSection(
                    header = stringResource(R.string.settings_scanning),
                    footer = stringResource(R.string.settings_scanning_footer),
                ) {
                    GroupedRow(
                        title = stringResource(R.string.scope_title),
                        value = if (ranges == 0) stringResource(R.string.scope_none) else ranges.toString(),
                        chevron = true,
                        onClick = onOpenScope,
                    )
                    GroupedRow(title = stringResource(R.string.mac_tip_row), chevron = true, onClick = onMacTipClick)
                }
            }
            tabbedTools.forEach { tool ->
                item(key = tool.route) { ToolTabsSection(tool, settings, onStartTab) }
            }
            item(key = "makers") { MakerListSection(makers, onDownloadMakers, onDeleteMakers) }
            item(key = "nfc") {
                GroupedSection(
                    header = stringResource(R.string.settings_nfc),
                    footer = stringResource(R.string.settings_card_details_footer),
                ) {
                    GroupedSwitchRow(
                        title = stringResource(R.string.settings_card_details),
                        checked = settings.showCardDetails,
                        onCheckedChange = onShowCardDetails,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ToolSettingsPreview() {
    AppTheme {
        ToolSettingsContent(
            settings = AppSettings(),
            tabbedTools = listOf(
                TabbedTool(
                    route = "nearby",
                    title = R.string.feature_nearby_title,
                    tabs = listOf(
                        ToolTab("wifi", R.string.nearby_tab_wifi, AppIcons.Wifi),
                        ToolTab("bluetooth", R.string.nearby_tab_bluetooth, AppIcons.Bluetooth),
                    ),
                ),
            ),
            onBack = {},
            onOpenScope = {},
            onMacTipClick = {},
            onStartTab = { _, _ -> },
        )
    }
}

/**
 * The optional IEEE maker list: whether it is on the device, and buttons to download, update, or delete it.
 * Downloading is the only time the app contacts the IEEE.
 */
@Composable
private fun MakerListSection(status: MakerListStatus, onDownload: () -> Unit, onDelete: () -> Unit) {
    val colors = AppTheme.colors
    val resources = LocalResources.current
    var confirmDelete by remember { mutableStateOf(false) }
    val count = status.count
    GroupedSection(
        header = stringResource(R.string.maker_list),
        footer = stringResource(R.string.maker_list_footer),
    ) {
        GroupedRow(
            title = stringResource(R.string.maker_list_title),
            subtitle = when {
                count == null -> stringResource(R.string.maker_list_none)
                else -> stringResource(
                    R.string.maker_list_info,
                    resources.getQuantityString(R.plurals.maker_list_count, count, count),
                    formatReportDate(status.downloadedAt ?: 0),
                    formatBytes(status.sizeBytes ?: 0),
                )
            },
        )
        when {
            status.downloading -> GroupedRow(
                title = stringResource(R.string.maker_list_downloading),
                titleColor = colors.secondaryLabel,
                trailing = { LoadingIndicator() },
            )
            else -> GroupedRow(
                title = stringResource(if (count == null) R.string.maker_list_download else R.string.maker_list_update),
                titleColor = colors.accent,
                onClick = onDownload,
            )
        }
        if (count != null && !status.downloading) {
            GroupedRow(
                title = stringResource(R.string.maker_list_delete),
                titleColor = colors.destructive,
                onClick = { confirmDelete = true },
            )
        }
        if (status.failed) {
            GroupedRow(stringResource(R.string.maker_list_failed), titleColor = colors.destructive)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.maker_list_delete_title),
            message = stringResource(R.string.maker_list_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
