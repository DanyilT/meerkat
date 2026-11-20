package com.danyt.meerkat.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** One choice in a [HoldMenu]. */
@Immutable
data class HoldMenuOption(val label: String, val icon: ImageVector? = null)

/**
 * The state of a [HoldMenu]: whether it is open, and where the finger is while it is still down.
 *
 * The finger stays in the window where the press began, while the menu is a popup window of its own. So both
 * sides are measured in screen coordinates.
 */
@Stable
class HoldMenuState {
    /** Whether the menu is on screen. */
    var isOpen by mutableStateOf(false)
        private set

    /** The option under the finger, while the finger that opened the menu is still down. */
    internal var highlighted by mutableStateOf<Int?>(null)

    /** The size of the element the menu belongs to, for a menu that covers it. */
    internal var anchorSize by mutableStateOf(IntSize.Zero)

    /** True from the long press until that finger lifts. */
    internal var following = false

    /** Where the finger was when the menu opened, and whether it has slid away from there since. */
    internal var start: Offset? = null
    internal var moved = false
    internal var anchor: LayoutCoordinates? = null
    internal val optionBounds = mutableMapOf<Int, Rect>()

    /** Opens the menu. Call it from the long press of the element the menu belongs to. */
    fun open() {
        // Positions from an earlier opening may be out of date, for example after scrolling.
        optionBounds.clear()
        isOpen = true
        following = true
        start = null
        moved = false
        highlighted = null
    }

    /** Closes the menu. */
    fun close() {
        isOpen = false
        following = false
        highlighted = null
    }

    internal fun optionAt(onScreen: Offset): Int? =
        optionBounds.entries.firstOrNull { it.value.contains(onScreen) }?.key

    /** Whether a point on the screen lies on the element the menu belongs to, or on the menu. */
    internal fun covers(onScreen: Offset): Boolean {
        val element = anchor?.takeIf { it.isAttached }?.let { Rect(it.localToScreen(Offset.Zero), it.size.toSize()) }
        // The options sit in one column, so the space from the first to the last one is the menu.
        val options = optionBounds.values
        val menu = if (options.isEmpty()) {
            null
        } else {
            Rect(
                left = options.minOf { it.left },
                top = options.minOf { it.top },
                right = options.maxOf { it.right },
                bottom = options.maxOf { it.bottom },
            )
        }
        return element?.contains(onScreen) == true || menu?.contains(onScreen) == true
    }
}

/** Remembers a [HoldMenuState]. */
@Composable
fun rememberHoldMenuState(): HoldMenuState = remember { HoldMenuState() }

/**
 * Lets the finger that long-pressed this element go on to pick from its [HoldMenu]: slide onto an option and
 * lift to choose it. Lifting without having slid leaves the menu open, to be tapped or dismissed, so a menu
 * that opens right under the finger never picks anything by itself. Lifting outside both the element and the
 * menu closes it.
 *
 * Put it on the element before its click handling, so it sees the touch first. While the menu follows the
 * finger, the touch is kept from scrolling the list underneath.
 */
fun Modifier.holdMenuSource(state: HoldMenuState, onSelect: (Int) -> Unit): Modifier = this
    .onGloballyPositioned {
        state.anchor = it
        state.anchorSize = it.size
    }
    .pointerInput(state) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            fun onScreen(position: Offset) = state.anchor?.takeIf { it.isAttached }?.localToScreen(position)
            // Where the finger was before the latest event: the point the menu opened at, once it opens.
            var last = onScreen(down.position)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                val here = onScreen(change.position)
                if (state.following) {
                    if (here != null) {
                        val start = state.start ?: (last ?: here).also { state.start = it }
                        if ((here - start).getDistance() > slop) state.moved = true
                    }
                    state.highlighted = here?.takeIf { state.moved }?.let(state::optionAt)
                    event.changes.forEach { it.consume() }
                    if (!change.pressed) {
                        val picked = state.highlighted
                        when {
                            picked != null -> {
                                state.close()
                                onSelect(picked)
                            }
                            // Lifted away from both the element and the menu: the user changed their mind.
                            here != null && !state.covers(here) -> state.close()
                            else -> {
                                state.following = false
                                state.highlighted = null
                            }
                        }
                    }
                }
                if (!state.following) last = here
                if (event.changes.none { it.pressed }) break
            }
        }
    }

/**
 * A menu that opens on a long press of the element it belongs to (see [holdMenuSource]) and can be picked from
 * without lifting the finger. Place it inside the same Box as that element.
 *
 * @param cover lays the menu over the element itself, at its size and in its [coverShape], with the options
 *   sharing its height. Otherwise it opens next to the element, like a [Menu].
 */
@Composable
fun HoldMenu(
    state: HoldMenuState,
    options: List<HoldMenuOption>,
    onSelect: (Int) -> Unit,
    cover: Boolean = false,
    coverShape: Shape = TileShape,
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val items: @Composable (Modifier) -> Unit = { itemModifier ->
        options.forEachIndexed { index, option ->
            MenuItem(
                text = option.label,
                icon = option.icon,
                highlighted = state.highlighted == index,
                onClick = {
                    state.close()
                    currentOnSelect(index)
                },
                modifier = itemModifier.onGloballyPositioned { coordinates ->
                    state.optionBounds[index] = Rect(coordinates.localToScreen(Offset.Zero), coordinates.size.toSize())
                },
            )
        }
    }
    if (!cover) {
        Menu(expanded = state.isOpen, onDismiss = state::close) { items(Modifier) }
        return
    }
    CoverMenu(state, coverShape) { items(Modifier.weight(1f)) }
}

/** The menu laid over its element: same place, same size, same shape. */
@Composable
private fun CoverMenu(
    state: HoldMenuState,
    shape: Shape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val animate = AppTheme.animationsEnabled
    // Kept on screen until the closing fade has finished.
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = state.isOpen
    if (!visible.currentState && !visible.targetState) return
    val size = with(LocalDensity.current) { state.anchorSize.toSize().toDpSize() }
    Popup(
        popupPositionProvider = OverAnchor,
        onDismissRequest = state::close,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = if (animate) {
                fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.94f)
            } else {
                EnterTransition.None
            },
            exit = if (animate) fadeOut(tween(110)) else ExitTransition.None,
        ) {
            Column(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(colors.elevated)
                    .border(1.dp, colors.outline, shape)
                    .padding(vertical = 8.dp),
                content = content,
            )
        }
    }
}

/** Puts the popup exactly where its anchor is. */
private object OverAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = anchorBounds.topLeft
}
