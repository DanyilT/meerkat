package com.danyt.meerkat.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.ConfirmDialog
import com.danyt.meerkat.core.design.EmptyState
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.IconButton
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.PrimaryButton

/** The dashboard: square tiles for the tools the user keeps here, with an edit mode to arrange them. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenFeature: (String) -> Unit,
    onOpenAllFeatures: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    // The layout as it was when editing started, so Revert can put it back.
    var layoutBeforeEdit by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val revert = {
        viewModel.setTileOrder(layoutBeforeEdit)
        editing = false
    }

    // Back while editing asks what to do with the changes. With nothing changed it simply leaves edit mode.
    BackHandler(enabled = editing) {
        if (state.tiles.map { it.route } == layoutBeforeEdit) editing = false else confirmExit = true
    }
    if (confirmExit) {
        ConfirmDialog(
            title = stringResource(R.string.edit_save_title),
            message = stringResource(R.string.edit_save_message),
            confirmLabel = stringResource(R.string.action_save),
            dismissLabel = stringResource(R.string.action_revert),
            onConfirm = {
                confirmExit = false
                editing = false
            },
            onSecondary = {
                confirmExit = false
                revert()
            },
            // Back or tapping outside closes only this message and stays in edit mode.
            onDismiss = { confirmExit = false },
        )
    }

    HomeContent(
        state = state,
        editing = editing,
        actions = HomeActions(
            onStartEditing = {
                layoutBeforeEdit = state.tiles.map { it.route }
                editing = true
            },
            onDoneEditing = { editing = false },
            onRevertEditing = revert,
            onOpenFeature = onOpenFeature,
            onOpenAllFeatures = onOpenAllFeatures,
            onOpenSettings = onOpenSettings,
            onReorder = viewModel::setTileOrder,
            onAdd = viewModel::addTile,
            onRemove = viewModel::removeTile,
        ),
    )
}

/** Everything the dashboard can ask its owner to do, grouped so the content takes one parameter for them. */
private class HomeActions(
    val onStartEditing: () -> Unit = {},
    val onDoneEditing: () -> Unit = {},
    val onRevertEditing: () -> Unit = {},
    val onOpenFeature: (String) -> Unit = {},
    val onOpenAllFeatures: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onReorder: (List<String>) -> Unit = {},
    val onAdd: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
)

@Composable
private fun HomeContent(state: HomeUiState, editing: Boolean, actions: HomeActions) {
    val animate = AppTheme.animationsEnabled
    AppScaffold(
        title = stringResource(if (editing) R.string.edit_dashboard else R.string.app_title),
        actions = { HomeBarButtons(editing, actions) },
    ) {
        if (state.loaded && state.tiles.isEmpty() && !editing) {
            item {
                EmptyState(
                    title = stringResource(R.string.dashboard_empty_title),
                    icon = AppIcons.Grid,
                    message = stringResource(R.string.dashboard_empty_message),
                    action = {
                        PrimaryButton(stringResource(R.string.edit_dashboard), onClick = actions.onStartEditing)
                    },
                    modifier = Modifier.fillParentMaxHeight(0.7f),
                )
            }
        } else {
            // Keys plus animateItem make the note fade in and out, and the tiles glide down and up to make room.
            if (editing) {
                item(key = "note") {
                    val hint = if (state.tiles.isEmpty()) R.string.edit_empty_hint else R.string.edit_drag_hint
                    Note(
                        text = stringResource(hint),
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                            .animated(this, animate),
                    )
                }
            }
            item(key = "tiles") {
                Box(Modifier.animated(this, animate)) {
                    TileGrid(state.tiles, editing, actions.onOpenFeature, actions.onRemove, actions.onReorder)
                }
            }
        }
        if (editing) {
            item(key = "gap") { Spacer(Modifier.height(26.dp)) }
            if (state.hidden.isNotEmpty()) {
                item(key = "add") {
                    GroupedSection(
                        modifier = Modifier.animated(this, animate),
                        header = stringResource(R.string.edit_add_header),
                    ) {
                        state.hidden.forEach { item ->
                            GroupedRow(
                                title = stringResource(item.title),
                                icon = item.icon,
                                onClick = { actions.onAdd(item.route) },
                                trailing = {
                                    Icon(
                                        imageVector = AppIcons.Plus,
                                        contentDescription = stringResource(R.string.action_add),
                                        tint = AppTheme.colors.accent,
                                        modifier = Modifier.size(22.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The top-right buttons. The normal set and the edit-mode set cross-fade when edit mode starts or ends. */
@Composable
private fun HomeBarButtons(editing: Boolean, actions: HomeActions) {
    val animate = AppTheme.animationsEnabled
    AnimatedContent(
        targetState = editing,
        transitionSpec = {
            if (animate) {
                fadeIn(tween(200)) togetherWith fadeOut(tween(120))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        contentAlignment = Alignment.CenterEnd,
        label = "actions",
    ) { inEditMode ->
        Row {
            if (inEditMode) {
                IconButton(AppIcons.Close, stringResource(R.string.edit_revert), actions.onRevertEditing)
                IconButton(AppIcons.Check, stringResource(R.string.edit_done), actions.onDoneEditing)
            } else {
                IconButton(AppIcons.Edit, stringResource(R.string.edit_dashboard), actions.onStartEditing)
                IconButton(AppIcons.Grid, stringResource(R.string.all_tools), actions.onOpenAllFeatures)
                IconButton(AppIcons.Settings, stringResource(R.string.settings), actions.onOpenSettings)
            }
        }
    }
}

/** Animates a lazy list item appearing, disappearing, and moving, unless animations are turned off. */
private fun Modifier.animated(scope: LazyItemScope, enabled: Boolean): Modifier =
    if (enabled) with(scope) { animateItem() } else this

private val PreviewTiles = listOf(
    HomeItem("lan", R.string.feature_lan_title, R.string.feature_lan_description, AppIcons.Lan, built = true),
    HomeItem("ssh", R.string.feature_ssh_title, R.string.feature_ssh_description, AppIcons.Terminal, built = false),
    HomeItem("tools", R.string.feature_tools_title, R.string.feature_tools_description, AppIcons.Tools, built = false),
)

@Preview
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        HomeContent(HomeUiState(PreviewTiles, loaded = true), editing = false, actions = HomeActions())
    }
}

@Preview
@Composable
private fun HomeScreenEditingPreview() {
    AppTheme {
        val state = HomeUiState(PreviewTiles.take(2), PreviewTiles.drop(2), loaded = true)
        HomeContent(state, editing = true, actions = HomeActions())
    }
}
