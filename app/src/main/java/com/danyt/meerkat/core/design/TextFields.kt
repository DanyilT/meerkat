package com.danyt.meerkat.core.design

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R

private val FieldShape = RoundedCornerShape(16.dp)

/**
 * Rounded, outlined text field with the label above it.
 *
 * @param label optional text shown above the field.
 * @param placeholder optional hint shown while the field is empty.
 * @param error when not null the field is outlined in red and this message is shown below it.
 * @param trailing optional content at the right end of the field, such as an [IconButton].
 * @param clearable show a button at the right end that empties the field, while it holds text.
 * @param onHelp when not null, a help button is shown at the right of the label.
 * @param transformInput optional filter applied to every edit. It gets the text before the edit and the text
 *   the keyboard asks for, and returns the text to show. When it changes the text, the cursor moves to the end.
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    transformInput: ((old: String, new: String) -> String)? = null,
    clearable: Boolean = false,
    onHelp: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    // The field keeps its own cursor position. When the text is replaced from outside, or rewritten by
    // transformInput, the cursor goes to the end, where typing continues.
    var edited by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Worked out on every pass instead of written back into state, which composition must not do.
    val field = if (edited.text == value) edited else TextFieldValue(value, TextRange(value.length))
    Column(modifier.alpha(if (enabled) 1f else 0.4f)) {
        if (label != null) {
            Row(Modifier.padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = AppTheme.typography.subhead,
                    color = colors.secondaryLabel,
                    modifier = Modifier.weight(1f),
                )
                if (onHelp != null) HelpButton(onHelp)
            }
        }
        BasicTextField(
            value = field,
            onValueChange = { change ->
                val text = transformInput?.invoke(field.text, change.text) ?: change.text
                edited = if (text == change.text) change else TextFieldValue(text, TextRange(text.length))
                if (text != value) onValueChange(text)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            textStyle = AppTheme.typography.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.card, FieldShape)
                        .border(
                            width = if (error != null) 1.5.dp else 1.dp,
                            color = if (error != null) colors.destructive else colors.outline,
                            shape = FieldShape,
                        )
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = AppTheme.typography.body,
                                color = colors.tertiaryLabel,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                    if (clearable && enabled && value.isNotEmpty()) {
                        val clearLabel = stringResource(R.string.action_clear)
                        FieldButton(AppIcons.Close, clearLabel, onClick = { onValueChange("") })
                    }
                    if (trailing != null) trailing()
                    if (trailing != null || (clearable && value.isNotEmpty())) Spacer(Modifier.width(4.dp))
                }
            },
        )
        if (error != null) {
            Text(
                text = error,
                style = AppTheme.typography.caption,
                color = colors.destructive,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

/**
 * An icon button that sits inside a [TextField], such as clear or a fill-in. Narrower than [IconButton] so
 * several fit side by side, but as tall as the field, so it stays easy to hit.
 */
@Composable
fun FieldButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FieldButton(contentDescription, onClick, modifier) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(22.dp))
    }
}

/** A [FieldButton] that shows any small [content] instead of an icon, such as an emoji. */
@Composable
fun FieldButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A small "?" button that opens an explanation. Sits next to a label or a line of text. */
@Composable
fun HelpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.Help,
            contentDescription = stringResource(R.string.action_help),
            tint = AppTheme.colors.accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Compact search field with a magnifier icon and a clear button once there is text. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search),
    onSearch: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val clearLabel = stringResource(R.string.search_clear)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = AppTheme.typography.body.copy(color = colors.label),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .background(colors.card, CircleShape)
                    .border(1.dp, colors.outline, CircleShape)
                    .heightIn(min = 42.dp)
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = null,
                    tint = colors.secondaryLabel,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(7.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = AppTheme.typography.body, color = colors.secondaryLabel, maxLines = 1)
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = clearLabel) { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier.size(18.dp).background(colors.tertiaryLabel, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = null,
                                tint = colors.background,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Preview
@Composable
private fun TextFieldsPreview() {
    AppTheme {
        var text by remember { mutableStateOf("192.168.1.0/24") }
        Column(
            modifier = Modifier.background(AppTheme.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SearchField(query = "", onQueryChange = {})
            SearchField(query = "router", onQueryChange = {})
            TextField(value = text, onValueChange = { text = it }, label = "Target")
            TextField(value = "", onValueChange = {}, label = "Host", placeholder = "example.com")
            TextField(value = "10.0.0.999", onValueChange = {}, label = "Address", error = "Not a valid IP address")
        }
    }
}
