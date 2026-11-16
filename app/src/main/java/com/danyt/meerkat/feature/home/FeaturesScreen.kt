package com.danyt.meerkat.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.Badge
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.IconButton

/** Every tool in one list, whether or not it is on the dashboard. */
@Composable
fun FeaturesScreen(
    viewModel: FeaturesViewModel,
    onOpenFeature: (String) -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeaturesContent(state, onOpenFeature, onOpenDashboard, onOpenSettings)
}

@Composable
private fun FeaturesContent(
    state: FeaturesUiState,
    onOpenFeature: (String) -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AppScaffold(
        title = stringResource(R.string.all_tools),
        onTitleClick = onOpenDashboard,
        actions = {
            IconButton(AppIcons.Home, stringResource(R.string.dashboard), onOpenDashboard)
            IconButton(AppIcons.Settings, stringResource(R.string.settings), onOpenSettings)
        },
    ) {
        item {
            GroupedSection {
                state.items.forEach { item ->
                    GroupedRow(
                        title = stringResource(item.title),
                        subtitle = stringResource(item.subtitle),
                        icon = item.icon,
                        chevron = true,
                        onClick = { onOpenFeature(item.route) },
                        trailing = if (item.built) null else ({ Badge(stringResource(R.string.soon)) }),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun FeaturesScreenPreview() {
    AppTheme {
        FeaturesContent(
            state = FeaturesUiState(
                listOf(
                    HomeItem("lan", R.string.feature_lan_title, R.string.feature_lan_description, AppIcons.Lan, true),
                    HomeItem(
                        route = "ssh",
                        title = R.string.feature_ssh_title,
                        subtitle = R.string.feature_ssh_description,
                        icon = AppIcons.Terminal,
                        built = false,
                    ),
                ),
            ),
            onOpenFeature = {},
            onOpenDashboard = {},
            onOpenSettings = {},
        )
    }
}
