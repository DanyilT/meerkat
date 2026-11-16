package com.danyt.meerkat.core.design

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
import androidx.compose.ui.text.input.ImeAction
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
) {
    val colors = AppTheme.colors
    Column(modifier.alpha(if (enabled) 1f else 0.4f)) {
        if (label != null) {
            Text(
                text = label,
                style = AppTheme.typography.subhead,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            textStyle = AppTheme.typography.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.card, FieldShape)
                        .border(
                            width = if (error != null) 1.5.dp else 1.dp,
                            color = if (error != null) colors.destructive else colors.outline,
                            shape = FieldShape,
                        )
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = AppTheme.typography.body, color = colors.tertiaryLabel, maxLines = 1)
                    }
                    innerTextField()
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
