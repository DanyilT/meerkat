package com.danyt.meerkat.feature.settings

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppSwitch
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.BottomSheet
import com.danyt.meerkat.core.design.ColorPicker
import com.danyt.meerkat.core.design.PrimaryButton
import com.danyt.meerkat.core.design.TextButton
import com.danyt.meerkat.core.design.toHexString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The app's own accent choices: eight hues at a similar strength, the default violet first and blue last. */
private val Presets = listOf(
    0xFF7C5CFC, 0xFFC04FD9, 0xFFE8528F, 0xFFE5484D, 0xFFF08A24, 0xFF2FB36A, 0xFF1FA6B8, 0xFF3B82F6,
).map { Color(it) }

private val RowShape = RoundedCornerShape(22.dp)

/**
 * Sheet for choosing the accent: presets, an HSV picker, and a monochrome option, with a preview at the bottom.
 * Every change is reported through [onChange] as it happens, so the app updates live. Swipe down to close.
 */
@Composable
fun AccentColorSheet(
    initialColor: Color,
    initialMonochrome: Boolean,
    onChange: (argb: Int, monochrome: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var argb by rememberSaveable { mutableIntStateOf(initialColor.toArgb()) }
    var monochrome by rememberSaveable { mutableStateOf(initialMonochrome) }

    BottomSheet(onDismiss = onDismiss, title = stringResource(R.string.settings_accent)) {
        AccentColorContent(
            color = Color(argb),
            monochrome = monochrome,
            onColorChange = {
                argb = it.toArgb()
                onChange(argb, monochrome)
            },
            onMonochromeChange = {
                monochrome = it
                onChange(argb, monochrome)
            },
        )
    }
}

@Composable
private fun AccentColorContent(
    color: Color,
    monochrome: Boolean,
    onColorChange: (Color) -> Unit,
    onMonochromeChange: (Boolean) -> Unit,
) {
    // The sheet follows the finger exactly, without the cross-fade the rest of the app uses for color changes.
    AppTheme(
        darkTheme = AppTheme.colors.isDark,
        accent = color,
        monochrome = monochrome,
        animationsEnabled = AppTheme.animationsEnabled,
        blurEnabled = AppTheme.blurEnabled,
    ) {
        val colors = AppTheme.colors
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.alpha(if (monochrome) 0.35f else 1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Each swatch sits in an equal share of the row, 48dp tall, so the touch target is full size
                // even though the circle is small.
                Row(Modifier.fillMaxWidth()) {
                    Presets.forEach { preset ->
                        val selected = !monochrome && preset.toArgb() == color.toArgb()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable(
                                    enabled = !monochrome,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClickLabel = preset.toHexString(),
                                ) {
                                    onColorChange(preset)
                                },
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
                if (monochrome) {
                    // Block touches while keeping the picker visible but dimmed.
                    Box {
                        ColorPicker(color, onColorChange = {})
                        Box(
                            Modifier.matchParentSize().pointerInput(Unit) {
                                awaitEachGesture { awaitFirstDown().consume() }
                            },
                        )
                    }
                } else {
                    ColorPicker(color, onColorChange)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RowShape)
                    .border(1.dp, colors.outline, RowShape)
                    .clickable(role = Role.Switch) { onMonochromeChange(!monochrome) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.accent_monochrome),
                        style = AppTheme.typography.body,
                        color = colors.label,
                    )
                    Text(
                        text = stringResource(R.string.accent_monochrome_description),
                        style = AppTheme.typography.caption,
                        color = colors.secondaryLabel,
                    )
                }
                AppSwitch(checked = monochrome, onCheckedChange = null)
            }

            PreviewCard(color, monochrome, onColorChange)
        }
    }
}

/** Shows the chosen accent on real controls, with the hex value that can be edited in place or copied. */
@Composable
private fun PreviewCard(color: Color, monochrome: Boolean, onColorChange: (Color) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(colors.card)
            .border(1.dp, colors.outline, RowShape)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(colors.accent, CircleShape))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (monochrome) {
                Text(
                    text = stringResource(R.string.accent_monochrome),
                    style = AppTheme.typography.headline,
                    color = colors.label,
                )
            } else {
                HexValue(color, onColorChange)
            }
        }
        TextButton(stringResource(R.string.accent_preview_link), onClick = {})
        PrimaryButton(stringResource(R.string.accent_preview_button), onClick = {})
    }
}

/**
 * The accent as a hex code. Tap to type a new six-character code in place, which applies as soon as it is
 * complete. Hold to copy the code.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HexValue(color: Color, onColorChange: (Color) -> Unit) {
    val colors = AppTheme.colors
    val hex = color.toHexString().removePrefix("#")
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val clipLabel = stringResource(R.string.accent_clip_label)

    if (copied) {
        LaunchedEffect(Unit) {
            delay(1500)
            copied = false
        }
    }

    Column {
        Text(
            text = when {
                copied -> stringResource(R.string.accent_hex_copied)
                editing -> stringResource(R.string.accent_hex_typing)
                else -> stringResource(R.string.accent_hex_hint)
            },
            style = AppTheme.typography.caption,
            color = colors.secondaryLabel,
        )
        if (editing) {
            var hadFocus by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#", style = AppTheme.typography.headline, color = colors.secondaryLabel)
                BasicTextField(
                    value = text,
                    onValueChange = { new ->
                        val clean = new.text.uppercase().filter { it in HEX_DIGITS }.take(HEX_LENGTH)
                        val cursor = new.selection.end.coerceAtMost(clean.length)
                        text = new.copy(text = clean, selection = TextRange(cursor))
                        if (clean.length == HEX_LENGTH) onColorChange(Color(0xFF000000 or clean.toLong(16)))
                    },
                    singleLine = true,
                    textStyle = AppTheme.typography.headline.copy(color = colors.label),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { editing = false }),
                    modifier = Modifier
                        .width(96.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) hadFocus = true else if (hadFocus) editing = false
                        },
                )
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            Text(
                text = "#$hex",
                style = AppTheme.typography.headline,
                color = colors.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(
                        onClickLabel = stringResource(R.string.accent_hex_edit),
                        onLongClickLabel = stringResource(R.string.accent_hex_copy),
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, "#$hex")))
                                copied = true
                            }
                        },
                        onClick = {
                            text = TextFieldValue(hex, selection = TextRange(0, hex.length))
                            editing = true
                        },
                    ),
            )
        }
    }
}

private const val HEX_DIGITS = "0123456789ABCDEF"
private const val HEX_LENGTH = 6

@Preview
@Composable
private fun AccentColorSheetPreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.background).padding(top = 16.dp)) {
            AccentColorContent(Color(0xFFF08A24), false, {}, {})
        }
    }
}
