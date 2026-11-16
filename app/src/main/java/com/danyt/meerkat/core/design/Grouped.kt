package com.danyt.meerkat.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat

private val SectionShape = RoundedCornerShape(22.dp)
private val RowInset = 16.dp

/**
 * An outlined, rounded card that groups related rows.
 *
 * Rows are separated by spacing only, with no divider lines. The section brings its own horizontal margin and
 * bottom spacing so sections can simply be stacked.
 *
 * @param header optional label above the card, in sentence case.
 * @param footer optional explanatory text below the card.
 */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp)) {
        if (header != null) {
            Text(
                text = header,
                style = AppTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = RowInset, end = RowInset, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SectionShape)
                .background(colors.card)
                .border(1.dp, colors.outline, SectionShape)
                .padding(vertical = 5.dp),
        ) {
            content()
        }
        if (footer != null) {
            Text(
                text = footer,
                style = AppTheme.typography.caption,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = RowInset, end = RowInset, top = 8.dp),
            )
        }
    }
}

/**
 * A row inside a [GroupedSection].
 *
 * @param title main text.
 * @param subtitle optional second line.
 * @param icon optional leading icon, shown on a round accent-colored tile.
 * @param value optional trailing text in the secondary color, for example the current choice.
 * @param chevron show a trailing chevron to signal navigation.
 * @param titleColor overrides the title color, for example the accent for an action or red for a destructive one.
 * @param onClick makes the whole row tappable when not null.
 * @param trailing optional slot placed after [value] and before the chevron.
 */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    chevron: Boolean = false,
    titleColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val click = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(click)
            .heightIn(min = 44.dp)
            .padding(horizontal = RowInset, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(32.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.typography.body,
                color = titleColor.takeOrElse { colors.label },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = AppTheme.typography.subhead, color = colors.secondaryLabel)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(value, style = AppTheme.typography.body, color = colors.secondaryLabel, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = colors.tertiaryLabel,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** A [GroupedRow] with a trailing switch. Tapping anywhere on the row toggles it. */
@Composable
fun GroupedSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    GroupedRow(
        title = title,
        modifier = modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        trailing = { AppSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/**
 * The app's switch. Off, it is an empty outlined track with a small dot. On, the track fills with the accent
 * and the dot grows as it slides across.
 *
 * @param onCheckedChange called on tap, or null when a parent (such as a row) handles the toggle.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (AppTheme.animationsEnabled) tween(170) else snap(),
        label = "switch",
    )
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Canvas(modifier.then(toggle).size(width = 46.dp, height = 26.dp).alpha(if (enabled) 1f else 0.4f)) {
        val stroke = 1.5.dp.toPx()
        val radius = size.height / 2
        val corner = CornerRadius(radius)
        drawRoundRect(lerp(Color.Transparent, colors.accent, position), cornerRadius = corner)
        drawRoundRect(
            color = lerp(colors.tertiaryLabel, colors.accent, position),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = corner,
            style = Stroke(stroke),
        )
        drawCircle(
            color = lerp(colors.secondaryLabel, colors.onAccent, position),
            radius = lerpFloat(6.dp.toPx(), 9.dp.toPx(), position),
            center = Offset(lerpFloat(radius, size.width - radius, position), radius),
        )
    }
}

@Preview
@Composable
private fun GroupedSectionPreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.background).padding(top = 16.dp)) {
            GroupedSection(header = "Appearance", footer = "Changes apply right away.") {
                GroupedRow(title = "Theme", value = "System", chevron = true, onClick = {})
                GroupedRow(
                    title = "LAN Scanner",
                    subtitle = "Find devices",
                    icon = AppIcons.Lan,
                    trailing = { Badge("Soon") },
                )
                GroupedSwitchRow(title = "Animations", checked = true, onCheckedChange = {})
                GroupedSwitchRow(title = "Monochrome", checked = false, onCheckedChange = {})
                GroupedRow(title = "Clear Cache", titleColor = AppTheme.colors.accent, onClick = {})
            }
        }
    }
}
