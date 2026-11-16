package com.danyt.meerkat.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A row of mutually exclusive options in an outlined pill. An accent-colored pill marks the selection and
 * slides to the new option when it changes.
 *
 * @param options labels, in display order. Works best with two to four short labels.
 * @param selectedIndex index of the selected option.
 * @param onSelected called with the index of the tapped option.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val animate = AppTheme.animationsEnabled
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, colors.outline, CircleShape)
            .padding(3.dp),
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        val indicatorX by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = if (animate) spring<Dp>(dampingRatio = 0.82f, stiffness = 420f) else snap(),
            label = "segment",
        )
        Box(Modifier.offset(x = indicatorX).width(segmentWidth).fillMaxHeight().background(colors.accent, CircleShape))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) colors.onAccent else colors.label,
                    animationSpec = if (animate) tween(200) else snap(),
                    label = "segmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(
                            selected = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelected(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = AppTheme.typography.subhead,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SegmentedControlPreview() {
    AppTheme {
        var selected by remember { mutableIntStateOf(0) }
        Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
            SegmentedControl(listOf("System", "Light", "Dark"), selected, onSelected = { selected = it })
        }
    }
}
