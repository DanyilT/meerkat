package com.danyt.meerkat.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.danyt.meerkat.R

private val InfoShape = RoundedCornerShape(28.dp)

/**
 * A card in the middle of the screen that explains something.
 *
 * Used for welcome messages and for help opened with a "?" or info button. It has no buttons of its own:
 * tapping the title, tapping outside, or back closes it, the same way a tap on a page title goes back.
 * Questions that need an answer use [ConfirmDialog] instead, which sits at the bottom edge.
 *
 * @param onDismiss called when the card is closed.
 * @param containerColor the card's color. Defaults to the theme's raised surface.
 * @param content the explanation, laid out in a column that scrolls when it is taller than the screen.
 */
@Composable
fun InfoDialog(
    title: String,
    onDismiss: () -> Unit,
    containerColor: Color = AppTheme.colors.elevated,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val noRipple = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss)
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            InfoCard(title, onDismiss, Modifier.clickable(noRipple, indication = null) {}, containerColor, content)
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.elevated,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val hazeState = rememberHazeState()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var titleHeight by remember { mutableIntStateOf(0) }
    val fadePx = with(density) { TitleFadeDistance.toPx() }
    // Like a screen title: the title stays put, and once the text scrolls under it, it turns into a glass bar.
    val covered by remember { derivedStateOf { (scroll.value / fadePx).coerceIn(0f, 1f) } }
    val blurSource = if (isBlurSupported()) Modifier.hazeSource(hazeState) else Modifier
    Box(
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(InfoShape)
            .background(containerColor)
            .border(1.dp, colors.outline, InfoShape),
    ) {
        Column(
            modifier = Modifier
                .then(blurSource)
                .verticalScroll(scroll)
                .padding(horizontal = 22.dp)
                .padding(top = with(density) { titleHeight.toDp() }, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        Box(Modifier.fillMaxWidth().onSizeChanged { titleHeight = it.height }) {
            GlassSurface(Modifier.matchParentSize().graphicsLayer { alpha = covered }, hazeState = hazeState)
            Hairline(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = covered })
            Text(
                text = title,
                style = AppTheme.typography.title.copy(fontWeight = FontWeight.SemiBold),
                color = colors.label,
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.action_close),
                        onClick = onClose,
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
        }
    }
}

private val TitleFadeDistance = 24.dp

/** A paragraph of body text inside an [InfoDialog]. */
@Composable
fun InfoText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.typography.subhead, color = AppTheme.colors.secondaryLabel, modifier = modifier)
}

/**
 * A paragraph in the info style where `%1$s` in [text] is replaced by [icon], drawn at the size of the text,
 * so the words can point at a button by showing it.
 */
@Composable
fun InfoTextWithIcon(text: String, icon: ImageVector) {
    val colors = AppTheme.colors
    val style = AppTheme.typography.subhead
    val content = buildAnnotatedString {
        append(text.substringBefore("%1\$s"))
        if ("%1\$s" in text) {
            appendInlineContent("icon", "[icon]")
            append(text.substringAfter("%1\$s"))
        }
    }
    val inlineContent = mapOf(
        "icon" to InlineTextContent(
            Placeholder(style.fontSize, style.fontSize, PlaceholderVerticalAlign.TextCenter),
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.fillMaxSize())
        },
    )
    Text(content, style = style, color = colors.secondaryLabel, inlineContent = inlineContent)
}

/** A small heading that starts a new part inside an [InfoDialog]. */
@Composable
fun InfoHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
        color = AppTheme.colors.label,
        modifier = modifier.padding(top = 4.dp).semantics { heading() },
    )
}

/**
 * One line of a list inside an [InfoDialog]: a short [label] in the label color, then what it means.
 * Give the items of one list the same [labelWidth] so their descriptions line up.
 */
@Composable
fun InfoItem(label: String, text: String, modifier: Modifier = Modifier, labelWidth: Dp = 76.dp) {
    val colors = AppTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = AppTheme.typography.subhead, color = colors.label, modifier = Modifier.width(labelWidth))
        Text(text, style = AppTheme.typography.subhead, color = colors.secondaryLabel, modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
private fun InfoDialogPreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
            InfoCard(title = "Port Ranges", onClose = {}) {
                InfoText("Ports are numbered 1 to 65535.")
                InfoHeading("Ranges")
                InfoItem("1-1023", "Well-known ports, used by standard services.")
                InfoItem("1024-49151", "Registered ports.")
            }
        }
    }
}
