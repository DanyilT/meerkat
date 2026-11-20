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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private val MenuShape = RoundedCornerShape(18.dp)
private val MenuGap = 4.dp
private val MenuMaxHeight = 380.dp

/**
 * A small list of choices that opens next to the element it belongs to. Place it inside the same Box as that
 * element. It opens below when there is room and above otherwise, a few dp away, lined up with the element's
 * right edge. Back or a tap outside closes it.
 */
@Composable
fun Menu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    val animate = AppTheme.animationsEnabled
    // Kept on screen until the closing fade has finished.
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return
    val gap = with(LocalDensity.current) { MenuGap.roundToPx() }
    Popup(
        popupPositionProvider = remember(gap) { AttachedPosition(gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = state,
            enter = if (animate) {
                fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.94f)
            } else {
                EnterTransition.None
            },
            exit = if (animate) fadeOut(tween(110)) else ExitTransition.None,
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 200.dp)
                    .clip(MenuShape)
                    .background(colors.elevated)
                    .border(1.dp, colors.outline, MenuShape)
                    // A long list scrolls instead of running off the screen.
                    .heightIn(max = MenuMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                content = content,
            )
        }
    }
}

/**
 * One choice in a [Menu]: a label, and optionally an icon on the left and a detail or a mark on the right.
 *
 * @param highlighted shows the item as the one under the finger, for menus picked by sliding to an item.
 */
@Composable
fun MenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (highlighted) colors.fill else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = AppTheme.typography.body, color = colors.label)
        if (trailing != null) {
            Spacer(Modifier.weight(1f).widthIn(min = 16.dp))
            trailing()
        }
    }
}

/** Below the anchor when the menu fits there, otherwise above it, [gap] pixels away; right edges aligned. */
private class AttachedPosition(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

@Preview
@Composable
private fun MenuPreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
            Column(
                Modifier
                    .width(220.dp)
                    .clip(MenuShape)
                    .background(AppTheme.colors.elevated)
                    .border(1.dp, AppTheme.colors.outline, MenuShape)
                    .padding(vertical = 6.dp),
            ) {
                MenuItem("Network", onClick = {}) {
                    Icon(AppIcons.Check, contentDescription = null, tint = AppTheme.colors.accent)
                }
                MenuItem("Cloudflare", onClick = {}) {
                    Text("1.1.1.1", style = AppTheme.typography.subhead, color = AppTheme.colors.secondaryLabel)
                }
            }
        }
    }
}
