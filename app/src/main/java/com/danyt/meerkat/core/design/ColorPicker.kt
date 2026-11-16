package com.danyt.meerkat.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import java.util.Locale
import kotlin.math.roundToInt

/** A color as hue (0 to 360), saturation (0 to 1), and value (0 to 1). */
private data class Hsv(val hue: Float, val saturation: Float, val value: Float) {
    fun clamped() = copy(saturation = saturation.coerceIn(0f, 1f), value = value.coerceIn(0f, 1f))

    fun toColor(): Color = Color.hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
}

private fun Color.toHsv(): Hsv {
    val max = maxOf(red, green, blue)
    val delta = max - minOf(red, green, blue)
    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta).mod(6f))
        max == green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }
    return Hsv(hue, if (max == 0f) 0f else delta / max, max)
}

/** Formats the color as an uppercase RGB hex string such as "#007AFF". Alpha is ignored. */
fun Color.toHexString(): String = String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)

/** How much one screen reader action changes saturation or brightness. */
private const val A11Y_STEP = 0.1f

private val HueColors = List(7) { Color.hsv(it * 60f % 360f, 1f, 1f) }

/**
 * HSV color picker: a saturation and brightness area with a hue slider below it.
 *
 * The picker is stateless about the chosen color. Pass the current [color] and store what [onColorChange]
 * reports. Colors are always fully opaque.
 */
@Composable
fun ColorPicker(color: Color, onColorChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    // Hue and saturation are kept here because they cannot be recovered from black, white, or grey.
    var hsv by remember { mutableStateOf(color.toHsv()) }
    if (hsv.toColor().toArgb() != color.toArgb()) hsv = color.toHsv()
    val onChange by rememberUpdatedState(onColorChange)
    fun update(new: Hsv) {
        hsv = new
        onChange(new.toColor())
    }

    // Screen readers cannot drag on a canvas, so both controls describe their value and can be adjusted
    // without touch: the shade area through four named actions, the hue bar as a standard slider.
    val shadeLabel = stringResource(R.string.picker_shade)
    val shadeState = stringResource(
        R.string.picker_shade_state,
        (hsv.saturation * 100).roundToInt(),
        (hsv.value * 100).roundToInt(),
    )
    val hueLabel = stringResource(R.string.picker_hue)
    val hueState = stringResource(R.string.picker_hue_state, hsv.hue.roundToInt())
    val shadeActions = listOf(
        stringResource(R.string.picker_more_saturation) to { hsv.copy(saturation = hsv.saturation + A11Y_STEP) },
        stringResource(R.string.picker_less_saturation) to { hsv.copy(saturation = hsv.saturation - A11Y_STEP) },
        stringResource(R.string.picker_more_brightness) to { hsv.copy(value = hsv.value + A11Y_STEP) },
        stringResource(R.string.picker_less_brightness) to { hsv.copy(value = hsv.value - A11Y_STEP) },
    )

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics {
                    contentDescription = shadeLabel
                    stateDescription = shadeState
                    customActions = shadeActions.map { (label, change) ->
                        CustomAccessibilityAction(label) {
                            update(change().clamped())
                            true
                        }
                    }
                }
                .pointerInput(Unit) {
                    dragOrTap { position ->
                        update(
                            hsv.copy(
                                saturation = (position.x / size.width).coerceIn(0f, 1f),
                                value = 1f - (position.y / size.height).coerceIn(0f, 1f),
                            ),
                        )
                    }
                },
        ) {
            val corner = CornerRadius(14.dp.toPx())
            val hueColor = Color.hsv(hsv.hue.coerceIn(0f, 360f), 1f, 1f)
            drawRoundRect(Brush.horizontalGradient(listOf(Color.White, hueColor)), cornerRadius = corner)
            drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), cornerRadius = corner)
            drawThumb(Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height), hsv.toColor())
        }
        Spacer(Modifier.height(16.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics {
                    contentDescription = hueLabel
                    stateDescription = hueState
                    progressBarRangeInfo = ProgressBarRangeInfo(hsv.hue, 0f..360f)
                    setProgress { degrees ->
                        update(hsv.copy(hue = degrees.coerceIn(0f, 360f)))
                        true
                    }
                }
                .pointerInput(Unit) {
                    dragOrTap { position -> update(hsv.copy(hue = (position.x / size.width).coerceIn(0f, 1f) * 360f)) }
                },
        ) {
            val inset = size.height / 2
            drawRoundRect(Brush.horizontalGradient(HueColors), cornerRadius = CornerRadius(inset))
            val x = inset + (hsv.hue / 360f) * (size.width - 2 * inset)
            drawThumb(Offset(x, inset), Color.hsv(hsv.hue.coerceIn(0f, 360f), 1f, 1f))
        }
    }
}

/** Reports the pointer position on press and for every move until release, consuming the gesture. */
private suspend fun PointerInputScope.dragOrTap(onPosition: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        onPosition(down.position)
        drag(down.id) { change ->
            change.consume()
            onPosition(change.position)
        }
    }
}

private fun DrawScope.drawThumb(center: Offset, fill: Color) {
    val radius = 11.dp.toPx()
    val clamped = Offset(center.x.coerceIn(0f, size.width), center.y.coerceIn(0f, size.height))
    drawCircle(Color.Black.copy(alpha = 0.25f), radius + 1.dp.toPx(), clamped, style = Stroke(1.dp.toPx()))
    drawCircle(fill, radius, clamped)
    drawCircle(Color.White, radius, clamped, style = Stroke(3.dp.toPx()))
}

@Preview
@Composable
private fun ColorPickerPreview() {
    AppTheme {
        var color by remember { mutableStateOf(DefaultAccent) }
        Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
            ColorPicker(color, onColorChange = { color = it })
        }
    }
}
