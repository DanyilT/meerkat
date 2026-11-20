package com.danyt.meerkat.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * One thing on a [RangeDiagram].
 *
 * @property distance how far from the centre, from 0 (at the centre) to 1 (the outer edge).
 * @property angle where around the centre, in degrees clockwise from the top. Usually not a real direction:
 *   phones cannot tell where a signal comes from, so callers spread points out evenly.
 * @property emphasized drawn filled in the accent color, such as the cell in use.
 */
@Immutable
data class RangePoint(
    val key: String,
    val label: String,
    val distance: Float,
    val angle: Float,
    val emphasized: Boolean = false,
)

/** A ring on a [RangeDiagram], at [distance] (0 to 1) from the centre, named by [label] (such as "10 m"). */
@Immutable
data class RangeRing(val distance: Float, val label: String)

/**
 * A round diagram with this device at the centre and things around it, closer to the centre when nearer or
 * stronger. Labels that would overlap are left out, strongest-placed first. Tapping a point calls [onSelect]
 * with its key.
 *
 * @param description what the diagram shows, for screen readers.
 */
@Composable
fun RangeDiagram(
    points: List<RangePoint>,
    rings: List<RangeRing>,
    centerLabel: String,
    description: String,
    modifier: Modifier = Modifier,
    onSelect: ((String) -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val ringStyle = AppTheme.typography.caption.copy(color = colors.tertiaryLabel)
    val labelStyle = AppTheme.typography.caption.copy(color = colors.label)
    val currentPoints by rememberUpdatedState(points)
    val currentOnSelect by rememberUpdatedState(onSelect)
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val select = currentOnSelect ?: return@detectTapGestures
                    val radius = size.width / 2f * RADIUS_SHARE
                    val center = Offset(size.width / 2f, size.height / 2f)
                    // The nearest point within a finger's width.
                    currentPoints
                        .map { it to position(center, radius, it) }
                        .minByOrNull { (_, at) -> hypot(at.x - tap.x, at.y - tap.y) }
                        ?.takeIf { (_, at) -> hypot(at.x - tap.x, at.y - tap.y) < 28.dp.toPx() }
                        ?.let { (point, _) -> select(point.key) }
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * RADIUS_SHARE
        val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        rings.forEach { ring ->
            drawCircle(
                color = colors.outline,
                radius = radius * ring.distance,
                center = center,
                style = Stroke(width = 1.dp.toPx(), pathEffect = dashes),
            )
        }

        // This device.
        drawCircle(colors.accent, radius = 6.dp.toPx(), center = center)
        drawCircle(colors.accent.copy(alpha = 0.2f), radius = 12.dp.toPx(), center = center)
        val me = measurer.measure(centerLabel, ringStyle)
        drawText(me, topLeft = Offset(center.x - me.size.width / 2f, center.y + 14.dp.toPx()))

        // The centre and its label are kept free of other labels.
        val taken = mutableListOf(
            Rect(
                left = center.x - me.size.width / 2f,
                top = center.y - 12.dp.toPx(),
                right = center.x + me.size.width / 2f,
                bottom = center.y + 14.dp.toPx() + me.size.height,
            ),
        )
        // Nearest first, so the closest things keep their labels when space runs out.
        points.sortedBy { it.distance }.forEach { point ->
            val at = position(center, radius, point)
            if (point.emphasized) {
                drawCircle(colors.accent, radius = 6.dp.toPx(), center = at)
            } else {
                drawCircle(colors.card, radius = 5.dp.toPx(), center = at)
                drawCircle(colors.accent, radius = 5.dp.toPx(), center = at, style = Stroke(width = 2.dp.toPx()))
            }
            val text = measurer.measure(
                text = point.label,
                style = labelStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = (size.width / 3).toInt()),
            )
            val left = (at.x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            val top = (at.y + 8.dp.toPx()).coerceAtMost(size.height - text.size.height)
            val box = Rect(left, top, left + text.size.width, top + text.size.height)
            if (taken.none { it.overlaps(box) }) {
                taken += box
                drawText(text, topLeft = Offset(left, top))
            }
            val dot = 7.dp.toPx()
            taken += Rect(at.x - dot, at.y - dot, at.x + dot, at.y + dot)
        }

        // Ring names last, left out where they would cover a point or its name.
        rings.forEach { ring ->
            val label = measurer.measure(ring.label, ringStyle)
            val topLeft = Offset(center.x + 4.dp.toPx(), center.y - radius * ring.distance - label.size.height)
            val box = Rect(topLeft, Size(label.size.width.toFloat(), label.size.height.toFloat()))
            if (taken.none { it.overlaps(box) }) drawText(label, topLeft = topLeft)
        }
    }
}

private fun position(center: Offset, radius: Float, point: RangePoint): Offset {
    val radians = Math.toRadians(point.angle.toDouble() - 90.0)
    val distance = radius * point.distance.coerceIn(0f, 1f)
    return Offset(center.x + (distance * cos(radians)).toFloat(), center.y + (distance * sin(radians)).toFloat())
}

/** How much of the half-width the outer ring uses, leaving room for labels at the edge. */
private const val RADIUS_SHARE = 0.86f

/**
 * A stable angle for [key], so a point keeps its place around the centre while the list updates. Spreads
 * points out without claiming a real direction.
 */
fun stableAngle(key: String): Float = ((key.hashCode() and 0x7FFFFFFF) % 360).toFloat()

@Preview
@Composable
private fun RangeDiagramPreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
            RangeDiagram(
                points = listOf(
                    RangePoint("a", "Headphones", 0.2f, 40f, emphasized = true),
                    RangePoint("b", "Watch", 0.45f, 160f),
                    RangePoint("c", "TV", 0.8f, 280f),
                ),
                rings = listOf(RangeRing(0.33f, "1 m"), RangeRing(0.66f, "5 m"), RangeRing(1f, "20 m")),
                centerLabel = "This device",
                description = "",
            )
        }
    }
}
