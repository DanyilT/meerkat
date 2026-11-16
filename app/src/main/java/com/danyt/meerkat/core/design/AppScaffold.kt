package com.danyt.meerkat.core.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danyt.meerkat.R
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

private val BarHeight = 60.dp
private val CollapseDistance = 24.dp
private const val CollapsedTitleScale = 0.72f
private const val BACK_REVEAL_MILLIS = 190
private const val TITLE_SWAP_MILLIS = 340
private val TitleAutoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 34.sp, stepSize = 1.sp)

/**
 * Screen container with a single top row: the title on the left and action buttons on the right.
 *
 * The content is a lazy list drawn edge to edge. As it scrolls under the top row, the title shrinks and the row
 * turns into a glass bar that blurs the content on Android 12+. System bar and keyboard insets are applied as
 * content padding.
 *
 * @param title shown large on the left. Shrinks to fit when it would collide with [actions].
 * @param onTitleClick when not null, tapping the title slides a back chevron in from the left and calls
 * this. Screens use it to go back, alongside the system back gesture.
 * @param actions buttons on the right of the title, for example [IconButton]s.
 * @param listState scroll state, hoist it to control or observe scrolling.
 * @param content list items placed below the top row.
 */
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val hazeState = rememberHazeState()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = insets.calculateTopPadding()
    val collapsePx = with(LocalDensity.current) { CollapseDistance.toPx() }
    val collapsed by remember(listState, collapsePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapsePx).coerceIn(0f, 1f)
        }
    }
    // Tapping the title goes back, and slides a back chevron in from the left, pushing the title aside.
    val animate = AppTheme.animationsEnabled
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(leaving) {
        if (leaving) {
            // The screen is normally gone well before this. If it stayed, hide the chevron again.
            delay(900)
            leaving = false
        }
    }
    val backLabel = stringResource(R.string.action_back)
    val titleClick = if (onTitleClick != null) {
        Modifier.clickable(enabled = !leaving, role = Role.Button, onClickLabel = backLabel) {
            // Go back at once. The chevron slides in while the screen is already sliding away.
            leaving = animate
            onTitleClick()
        }
    } else {
        Modifier
    }
    val sideInsets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))

    // Capturing the list for blur has a cost, so it is skipped entirely while blur is off or unsupported.
    val blurSource = if (isBlurSupported()) Modifier.hazeSource(hazeState) else Modifier
    Box(modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().then(blurSource).then(sideInsets),
            state = listState,
            contentPadding = PaddingValues(
                top = topInset + BarHeight + 8.dp,
                bottom = insets.calculateBottomPadding() + 32.dp,
            ),
            content = content,
        )

        Box(Modifier.fillMaxWidth().height(topInset + BarHeight)) {
            GlassSurface(Modifier.matchParentSize().graphicsLayer { alpha = collapsed }, hazeState = hazeState)
            Hairline(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = collapsed })
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset)
                    .height(BarHeight)
                    .then(sideInsets)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                val scale = 1f - (1f - CollapsedTitleScale) * collapsed
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .then(titleClick)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedVisibility(
                            visible = leaving,
                            enter = expandHorizontally(tween(BACK_REVEAL_MILLIS), expandFrom = Alignment.Start) +
                                slideInHorizontally(tween(BACK_REVEAL_MILLIS)) { -it } +
                                fadeIn(tween(BACK_REVEAL_MILLIS)),
                            exit = ExitTransition.None,
                        ) {
                            Icon(
                                imageVector = AppIcons.ChevronLeft,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.padding(end = 14.dp).size(28.dp),
                            )
                        }
                        // When the title changes on the same screen, the old one lifts away and the new one rises in.
                        AnimatedContent(
                            targetState = title,
                            modifier = Modifier.weight(1f, fill = false),
                            transitionSpec = {
                                if (animate) {
                                    // Both titles move the same short distance on the same curve while they
                                    // cross-fade, and
                                    // the width switches at once instead of animating, so nothing re-measures mid-way.
                                    val move = tween<IntOffset>(TITLE_SWAP_MILLIS, easing = FastOutSlowInEasing)
                                    val enter = fadeIn(tween(TITLE_SWAP_MILLIS, delayMillis = 60)) +
                                        slideInVertically(move) { it / 4 }
                                    val exit = fadeOut(tween(TITLE_SWAP_MILLIS - 100)) +
                                        slideOutVertically(move) { -it / 4 }
                                    (enter togetherWith exit).using(SizeTransform(clip = false) { _, _ -> snap() })
                                } else {
                                    EnterTransition.None togetherWith ExitTransition.None
                                }
                            },
                            label = "title",
                        ) { text ->
                            Text(
                                text = text,
                                style = AppTheme.typography.largeTitle,
                                color = colors.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                autoSize = TitleAutoSize,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                    }
                }
                actions()
            }
        }
    }
}

/** A one-pixel divider in the separator color. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val thickness = with(LocalDensity.current) { 1f.toDp() }
    Box(modifier.fillMaxWidth().height(thickness).background(AppTheme.colors.separator))
}

@Preview
@Composable
private fun AppScaffoldPreview() {
    AppTheme {
        AppScaffold(
            title = "Meerkat Toolbox",
            onTitleClick = {},
            actions = { IconButton(AppIcons.Settings, contentDescription = "Settings", onClick = {}) },
        ) {
            item {
                GroupedSection(header = "Network") {
                    GroupedRow(title = "LAN Scanner", icon = AppIcons.Lan, chevron = true, onClick = {})
                    GroupedRow(title = "Port Scanner", icon = AppIcons.Ports, chevron = true, onClick = {})
                }
            }
        }
    }
}
