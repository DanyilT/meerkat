package com.danyt.meerkat.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** The shape of a [Tile], shared with things laid over one, such as a covering [HoldMenu]. */
internal val TileShape = RoundedCornerShape(26.dp)
private val CompactTileWidth = 160.dp

/**
 * A square dashboard tile: icon at the top, title and optional subtitle at the bottom.
 *
 * The tile is always square. Give it a width (for example a weight in a row) and it sizes its height to match.
 *
 * @param onClick makes the tile tappable when not null.
 * @param badge optional small label in the top right corner, for example "Soon".
 * @param badgeEmphasized fills the badge with the accent color, for something that wants attention, like "New".
 * @param action optional slot in the top right corner, shown instead of [badge], for example a remove button.
 * @param onLongClick called on a long press, when [onClick] is set too.
 * @param onLongClickLabel what the long press does, for accessibility services.
 */
@Composable
fun Tile(
    title: String,
    icon: ImageVector,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    badgeEmphasized: Boolean = false,
    action: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val colors = AppTheme.colors
    val click = when {
        onClick == null -> Modifier
        onLongClick != null -> Modifier.combinedClickable(
            role = Role.Button,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        else -> Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(TileShape)
            .background(colors.card)
            .border(1.dp, colors.outline, TileShape)
            .then(click),
    ) {
        // On narrow screens the tile gets small, so its icon and padding shrink and the subtitle keeps one line.
        val compact = maxWidth < CompactTileWidth
        val iconTile = if (compact) 36.dp else 46.dp
        Box(Modifier.fillMaxSize().padding(if (compact) 12.dp else 16.dp)) {
            Box(Modifier.size(iconTile).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(iconTile * 0.58f),
                )
            }
            if (action != null) {
                Box(Modifier.align(Alignment.TopEnd)) { action() }
            } else if (badge != null) {
                Badge(badge, Modifier.align(Alignment.TopEnd), emphasized = badgeEmphasized)
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = title,
                    style = if (compact) AppTheme.typography.subhead else AppTheme.typography.headline,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = if (compact) AppTheme.typography.caption else AppTheme.typography.subhead,
                        color = colors.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 380)
@Composable
private fun TilePreview() {
    AppTheme {
        Row(
            modifier = Modifier.background(AppTheme.colors.background).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Tile("LAN Scanner", AppIcons.Lan, onClick = {}, modifier = Modifier.weight(1f), subtitle = "Find devices.")
            Tile("SSH", AppIcons.Terminal, onClick = {}, modifier = Modifier.weight(1f), badge = "Soon")
            Tile(
                title = "Hotspot",
                icon = AppIcons.Hotspot,
                onClick = {},
                modifier = Modifier.weight(1f),
                badge = "New",
                badgeEmphasized = true,
            )
        }
    }
}
