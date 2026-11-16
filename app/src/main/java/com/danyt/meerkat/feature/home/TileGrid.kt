package com.danyt.meerkat.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.Tile
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val MinTileWidth = 170.dp
private val TileGap = 12.dp
private val TileBacking = RoundedCornerShape(26.dp)
private val RemoveTouchTarget = 48.dp

/** How visible tiles are in edit mode: slightly dimmed at rest, and dimmed further while another is held. */
private const val EDIT_DIM = 0.72f
private const val UNFOCUSED_DIM = 0.4f
private const val TILE_SETTLE_MILLIS = 150

/**
 * Lays tiles out in as many equal columns as fit, never fewer than two. While [editing], a tile can be dragged
 * to a new slot and removed with its corner button. [onReorder] is called once, when a drag ends.
 *
 * Where tiles go is decided by [TileGridMath]. This composable only handles touch, animation, and drawing.
 */
@Composable
internal fun TileGrid(
    tiles: List<HomeItem>,
    editing: Boolean,
    onOpenFeature: (String) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    // Working copy so tiles can move while a drag is in progress, before the new order is saved.
    var order by remember { mutableStateOf(tiles) }
    var draggedRoute by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // The tile under the finger, from the moment it is touched. It stays in focus while the rest dim.
    var pressedRoute by remember { mutableStateOf<String?>(null) }
    var settling by remember { mutableStateOf<Job?>(null) }
    // Take saved changes (add, remove, revert) only while no tile is held or settling. A save from the last
    // drop can arrive after the next drag has begun, and must not rearrange tiles under the finger.
    if (draggedRoute == null && order != tiles) order = tiles

    val backing = AppTheme.colors.background
    val animate = AppTheme.animationsEnabled
    val scope = rememberCoroutineScope()

    // Saves the new order, then quickly eases the tile from where it was dropped into its slot.
    fun release() {
        onReorder(order.map { it.route })
        settling = scope.launch {
            if (animate) {
                val settle = tween<Offset>(TILE_SETTLE_MILLIS, easing = FastOutSlowInEasing)
                animate(Offset.VectorConverter, dragOffset, Offset.Zero, animationSpec = settle) { value, _ ->
                    dragOffset = value
                }
            }
            dragOffset = Offset.Zero
            draggedRoute = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val density = LocalDensity.current
        val grid = remember(constraints.maxWidth, density) {
            with(density) { TileGridMath.fit(constraints.maxWidth, MinTileWidth.roundToPx(), TileGap.roundToPx()) }
        }
        fun slot(index: Int) = IntOffset(grid.slotX(index), grid.slotY(index))

        Box(Modifier.fillMaxWidth().height(with(density) { grid.heightPx(order.size).toDp() })) {
            order.forEachIndexed { index, item ->
                key(item.route) {
                    val dragged = draggedRoute == item.route
                    val focused = editing && (draggedRoute ?: pressedRoute) == item.route
                    val anyFocused = (draggedRoute ?: pressedRoute) != null
                    val target = slot(index)
                    val settled by animateIntOffsetAsState(
                        targetValue = target,
                        animationSpec = if (animate && !dragged) {
                            spring(stiffness = Spring.StiffnessMediumLow)
                        } else {
                            snap()
                        },
                        label = "tile",
                    )
                    val emphasis by animateFloatAsState(
                        targetValue = when {
                            !editing || focused -> 1f
                            anyFocused -> UNFOCUSED_DIM
                            else -> EDIT_DIM
                        },
                        animationSpec = if (animate) tween(220) else snap(),
                        label = "tileEmphasis",
                    )
                    val lift by animateFloatAsState(
                        targetValue = if (focused) 1.05f else 1f,
                        animationSpec = if (animate) spring(stiffness = Spring.StiffnessMedium) else snap(),
                        label = "tileLift",
                    )
                    val press = if (editing) {
                        Modifier.pointerInput(item.route) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                pressedRoute = item.route
                                do {
                                    val event = awaitPointerEvent()
                                } while (event.changes.any { it.pressed })
                                if (pressedRoute == item.route) pressedRoute = null
                            }
                        }
                    } else {
                        Modifier
                    }
                    val drag = if (editing) {
                        Modifier.pointerInput(item.route, grid) {
                            detectDragGestures(
                                onDragStart = {
                                    settling?.cancel()
                                    // Picking the same tile up again mid-settle continues from where it is,
                                    // with no jump.
                                    if (draggedRoute != item.route) dragOffset = Offset.Zero
                                    draggedRoute = item.route
                                },
                                onDragEnd = { release() },
                                onDragCancel = { release() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    val from = order.indexOfFirst { it.route == item.route }
                                    val moved = dragOffset.round()
                                    val to = grid.targetIndex(from, moved.x, moved.y, order.size)
                                    if (from >= 0 && to != from) {
                                        order = order.moved(from, to)
                                        // Keep the tile under the finger now that its home slot has changed.
                                        val shift = slot(from) - slot(to)
                                        dragOffset += Offset(shift.x.toFloat(), shift.y.toFloat())
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                    val title = stringResource(item.title)
                    Tile(
                        title = title,
                        icon = item.icon,
                        onClick = if (editing) null else ({ onOpenFeature(item.route) }),
                        modifier = Modifier
                            .zIndex(if (focused) 1f else 0f)
                            .offset { if (dragged) target + dragOffset.round() else settled }
                            .size(with(density) { grid.tilePx.toDp() })
                            .graphicsLayer {
                                scaleX = lift
                                scaleY = lift
                                alpha = emphasis
                            }
                            // Tiles are translucent. A solid backing keeps the lifted tile from showing the
                            // ones beneath it.
                            .then(if (focused) Modifier.background(backing, TileBacking) else Modifier)
                            .then(press)
                            .then(drag),
                        subtitle = stringResource(item.subtitle),
                        badge = if (item.built) null else stringResource(R.string.soon),
                        action = if (editing) ({ RemoveButton(title) { onRemove(item.route) } }) else null,
                    )
                }
            }
        }
    }
}

/** The round remove button on a tile in edit mode. It looks small but has a full-size touch target. */
@Composable
private fun RemoveButton(title: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    // Pops in when edit mode starts.
    val animate = AppTheme.animationsEnabled
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) }
    Box(
        modifier = Modifier
            // Extend the touch area up and to the right, into the tile's padding, without moving the circle.
            .offset(x = (RemoveTouchTarget - 32.dp) / 2, y = -(RemoveTouchTarget - 32.dp) / 2)
            .size(RemoveTouchTarget)
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
                alpha = appear.value.coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.edit_remove_tile, title),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(32.dp).background(colors.fill, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = stringResource(R.string.action_remove),
                tint = colors.label,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
