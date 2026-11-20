package com.danyt.meerkat.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AccentPresets
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.ConfirmDialog
import com.danyt.meerkat.core.design.EmptyState
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.IconButton
import com.danyt.meerkat.core.design.InfoHeading
import com.danyt.meerkat.core.design.InfoItem
import com.danyt.meerkat.core.design.InfoText
import com.danyt.meerkat.core.design.InfoTextWithIcon
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.PrimaryButton
import com.danyt.meerkat.core.design.SegmentedControl
import com.danyt.meerkat.core.design.animated
import com.danyt.meerkat.core.design.toHexString
import com.danyt.meerkat.core.help.IntroDialog
import com.danyt.meerkat.core.help.IntroKeys
import com.danyt.meerkat.core.help.Intros
import com.danyt.meerkat.core.help.rememberIntro
import com.danyt.meerkat.core.settings.ThemeMode

/** The dashboard: square tiles for the tools the user keeps here, with an edit mode to arrange them. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    intros: Intros,
    onOpenFeature: (String) -> Unit,
    onOpenAllFeatures: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    // The layout as it was when editing started, so Revert can put it back.
    var layoutBeforeEdit by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val intro = rememberIntro(intros, IntroKeys.HOME)
    // Wait for the saved layout, so the welcome lists the right tools from its first frame.
    if (state.loaded) {
        IntroDialog(intro, stringResource(R.string.intro_home_title)) {
            HomeIntro(state, viewModel::setThemeMode, viewModel::setAccent)
        }
    }
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
                        // The card grows and shrinks smoothly, and a tool that was just taken off the
                        // dashboard fades and unfolds into the list.
                        Column(if (animate) Modifier.animateContentSize(tween(220)) else Modifier) {
                            state.hidden.forEach { item ->
                                key(item.route) {
                                    val shown = remember {
                                        MutableTransitionState(!animate).apply { targetState = true }
                                    }
                                    AnimatedVisibility(
                                        visibleState = shown,
                                        enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                                    ) {
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
        }
    }
}

/** The welcome: what the app is, which tools work today, and the two quickest appearance choices. */
@Composable
private fun ColumnScope.HomeIntro(state: HomeUiState, onTheme: (ThemeMode) -> Unit, onAccent: (Int) -> Unit) {
    val colors = AppTheme.colors
    InfoText(stringResource(R.string.intro_home_what))
    InfoHeading(stringResource(R.string.intro_home_tools))
    // Each tool as its icon and name, with the one-line description below. Spacing keeps them apart.
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        (state.tiles + state.hidden).filter { it.built }.forEach { tool ->
            Column {
                val titleStyle = AppTheme.typography.headline
                val iconSize = with(LocalDensity.current) { titleStyle.fontSize.toDp() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(tool.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(iconSize))
                    Text(
                        text = stringResource(tool.title),
                        style = titleStyle,
                        color = colors.label,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    text = stringResource(tool.subtitle),
                    style = AppTheme.typography.subhead,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(start = iconSize + 8.dp, top = 2.dp),
                )
            }
        }
    }
    InfoTextWithIcon(stringResource(R.string.intro_home_dashboard), AppIcons.Edit)
    InfoText(stringResource(R.string.intro_home_back))
    InfoHeading(stringResource(R.string.settings_appearance))
    val themeLabels = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
    SegmentedControl(
        options = themeLabels.map { stringResource(it) },
        selectedIndex = state.themeMode.ordinal,
        onSelected = { onTheme(ThemeMode.entries[it]) },
    )
    // The same swatches as the accent sheet in Settings: a full-height touch area with no press highlight.
    Row(Modifier.fillMaxWidth()) {
        AccentPresets.forEach { preset ->
            val selected = preset.toArgb() == state.accentColor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = preset.toHexString(),
                    ) { onAccent(preset.toArgb()) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .border(2.dp, if (selected) colors.label else Color.Transparent, CircleShape)
                        .padding(5.dp)
                        .background(preset, CircleShape),
                )
            }
        }
    }
    InfoText(stringResource(R.string.intro_home_more))
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
