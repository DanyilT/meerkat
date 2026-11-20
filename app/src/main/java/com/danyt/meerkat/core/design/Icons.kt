package com.danyt.meerkat.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Line icons drawn on a 24x24 grid with rounded strokes.
 *
 * They are built in code so the app does not need an icon library. Tint them with `Icon(tint = ...)`.
 */
object AppIcons {
    /** Every icon with its name, in display order. Used by the preview below. */
    val all: List<Pair<String, ImageVector>> by lazy {
        listOf(
            ChevronRight, ChevronLeft, ChevronUp, ChevronDown, Check, Close, Search, Clock, Info, Home, Plus,
            Edit, Grid,
            Lan, Ports, Terminal, Traffic, Tools, Reports, Settings, Hotspot, Export, Trash, Wifi, Ethernet,
            Pin, Help, Shield, Nearby, Bluetooth, Cell, Nfc,
        ).map { it.name to it }
    }

    val ChevronRight by lazy { icon("ChevronRight", 2.2f) { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) } }
    val ChevronLeft by lazy { icon("ChevronLeft", 2.2f) { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) } }
    val ChevronUp by lazy { icon("ChevronUp", 2.2f) { moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f) } }
    val ChevronDown by lazy { icon("ChevronDown", 2.2f) { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) } }
    val Check by lazy { icon("Check", 2.2f) { moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7f) } }
    val Close by lazy {
        icon("Close", 2.2f) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    }
    val Search by lazy { icon("Search", 2f) { circle(10.5f, 10.5f, 6.5f); moveTo(15.5f, 15.5f); lineTo(20f, 20f) } }
    val Clock by lazy {
        icon("Clock") { circle(12f, 12f, 9f); moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15f, 14f) }
    }
    val Info by lazy {
        icon("Info", 2f) {
            circle(12f, 12f, 9f)
            moveTo(12f, 11f); lineTo(12f, 16.5f)
            moveTo(12f, 7.6f); lineTo(12f, 7.7f)
        }
    }
    val Home by lazy {
        icon("Home") {
            moveTo(3.5f, 11.5f); lineTo(12f, 4f); lineTo(20.5f, 11.5f)
            moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f)
        }
    }
    val Hotspot by lazy {
        icon("Hotspot") {
            circle(12f, 13f, 1.6f)
            moveTo(7.8f, 17.2f); arcTo(6f, 6f, 0f, true, true, 16.2f, 17.2f)
            moveTo(5.3f, 19.7f); arcTo(9.5f, 9.5f, 0f, true, true, 18.7f, 19.7f)
        }
    }
    val Wifi by lazy {
        icon("Wifi") {
            circle(12f, 18.2f, 0.9f)
            moveTo(8.6f, 14.8f); arcTo(4.8f, 4.8f, 0f, false, true, 15.4f, 14.8f)
            moveTo(5.6f, 11.6f); arcTo(9f, 9f, 0f, false, true, 18.4f, 11.6f)
            moveTo(2.8f, 8.4f); arcTo(13f, 13f, 0f, false, true, 21.2f, 8.4f)
        }
    }
    val Ethernet by lazy {
        icon("Ethernet") {
            moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 15f); lineTo(16.5f, 15f); lineTo(16.5f, 19f)
            lineTo(7.5f, 19f); lineTo(7.5f, 15f); lineTo(4f, 15f); close()
            moveTo(8.5f, 8.5f); lineTo(8.5f, 11f)
            moveTo(12f, 8.5f); lineTo(12f, 11f)
            moveTo(15.5f, 8.5f); lineTo(15.5f, 11f)
        }
    }
    /** A pin with a round head, standing straight up: "here", this device. */
    val Pin by lazy {
        icon("Pin") {
            // A solid ball, so it does not read as a magnifying glass: rings close enough to fill it.
            circle(12f, 7f, 0.9f)
            circle(12f, 7f, 1.9f)
            circle(12f, 7f, 2.8f)
            moveTo(12f, 10.5f); lineTo(12f, 21f)
        }
    }
    val Help by lazy {
        icon("Help") {
            circle(12f, 12f, 9f)
            moveTo(9.4f, 9.6f); arcTo(2.7f, 2.7f, 0f, true, true, 12f, 12.9f); lineTo(12f, 13.8f)
            moveTo(12f, 16.6f); lineTo(12f, 16.7f)
        }
    }
    val Shield by lazy {
        icon("Shield") {
            moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 11f); arcTo(9.5f, 9.5f, 0f, false, true, 12f, 21f)
            arcTo(9.5f, 9.5f, 0f, false, true, 5f, 11f); lineTo(5f, 6f); close()
        }
    }

    /** Signals spreading out on both sides of a point: things in range. */
    val Nearby by lazy {
        icon("Nearby") {
            circle(12f, 12f, 1.6f)
            moveTo(8.5f, 8.5f); arcTo(5f, 5f, 0f, false, false, 8.5f, 15.5f)
            moveTo(15.5f, 8.5f); arcTo(5f, 5f, 0f, false, true, 15.5f, 15.5f)
            moveTo(5.6f, 5.6f); arcTo(9f, 9f, 0f, false, false, 5.6f, 18.4f)
            moveTo(18.4f, 5.6f); arcTo(9f, 9f, 0f, false, true, 18.4f, 18.4f)
        }
    }
    val Bluetooth by lazy {
        icon("Bluetooth") {
            moveTo(7f, 7.5f); lineTo(16.5f, 16f); lineTo(12f, 20.5f); lineTo(12f, 3.5f); lineTo(16.5f, 8f)
            lineTo(7f, 16.5f)
        }
    }

    /** A mast with signal on both sides, for mobile network cells. */
    val Cell by lazy {
        icon("Cell") {
            circle(12f, 8.5f, 1.4f)
            moveTo(12f, 10f); lineTo(8f, 21f)
            moveTo(12f, 10f); lineTo(16f, 21f)
            moveTo(9.4f, 17f); lineTo(14.6f, 17f)
            moveTo(8.2f, 5f); arcTo(5f, 5f, 0f, false, false, 8.2f, 12f)
            moveTo(15.8f, 5f); arcTo(5f, 5f, 0f, false, true, 15.8f, 12f)
        }
    }

    /** Four arcs of growing size bulging to the right, like the contactless symbol. */
    val Nfc by lazy {
        icon("Nfc") {
            moveTo(3.7f, 9.5f); arcTo(3.2f, 3.2f, 0f, false, true, 3.7f, 14.5f)
            moveTo(7.7f, 7f); arcTo(6.5f, 6.5f, 0f, false, true, 7.7f, 17f)
            moveTo(11.7f, 4.5f); arcTo(9.8f, 9.8f, 0f, false, true, 11.7f, 19.5f)
            moveTo(15.7f, 2.5f); arcTo(13f, 13f, 0f, false, true, 15.7f, 21.5f)
        }
    }
    val Export by lazy {
        icon("Export") {
            moveTo(12f, 15f); lineTo(12f, 4f)
            moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
            moveTo(5f, 13f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 13f)
        }
    }
    val Trash by lazy {
        icon("Trash") {
            moveTo(5f, 7f); lineTo(19f, 7f)
            moveTo(10f, 7f); lineTo(10f, 4.5f); lineTo(14f, 4.5f); lineTo(14f, 7f)
            moveTo(7f, 7f); lineTo(8f, 20f); lineTo(16f, 20f); lineTo(17f, 7f)
        }
    }
    val Plus by lazy { icon("Plus", 2.2f) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } }
    val Edit by lazy {
        icon("Edit") {
            moveTo(4f, 20f); lineTo(5f, 16f); lineTo(16f, 5f); lineTo(19f, 8f); lineTo(8f, 19f); close()
            moveTo(13.5f, 7.5f); lineTo(16.5f, 10.5f)
        }
    }
    val Grid by lazy {
        icon("Grid") {
            roundRect(4f, 4f, 6.5f, 6.5f, 1.6f)
            roundRect(13.5f, 4f, 6.5f, 6.5f, 1.6f)
            roundRect(4f, 13.5f, 6.5f, 6.5f, 1.6f)
            roundRect(13.5f, 13.5f, 6.5f, 6.5f, 1.6f)
        }
    }
    val Lan by lazy {
        icon("Lan") {
            roundRect(9f, 3f, 6f, 5f, 1.2f)
            roundRect(3f, 16f, 6f, 5f, 1.2f)
            roundRect(15f, 16f, 6f, 5f, 1.2f)
            moveTo(12f, 8f); lineTo(12f, 12f)
            moveTo(6f, 16f); lineTo(6f, 12f); lineTo(18f, 12f); lineTo(18f, 16f)
        }
    }
    val Ports by lazy {
        icon("Ports") {
            roundRect(3f, 6f, 18f, 12f, 2.5f)
            moveTo(7.5f, 10f); lineTo(7.5f, 14f)
            moveTo(12f, 10f); lineTo(12f, 14f)
            moveTo(16.5f, 10f); lineTo(16.5f, 14f)
        }
    }
    val Terminal by lazy {
        icon("Terminal") {
            roundRect(3f, 4f, 18f, 16f, 3f)
            moveTo(7f, 9f); lineTo(10f, 12f); lineTo(7f, 15f)
            moveTo(13f, 15f); lineTo(17f, 15f)
        }
    }
    val Traffic by lazy {
        icon("Traffic") {
            moveTo(8f, 20f); lineTo(8f, 5f)
            moveTo(4f, 9f); lineTo(8f, 5f); lineTo(12f, 9f)
            moveTo(16f, 4f); lineTo(16f, 19f)
            moveTo(12f, 15f); lineTo(16f, 19f); lineTo(20f, 15f)
        }
    }
    val Tools by lazy {
        icon("Tools") {
            moveTo(4f, 7f); lineTo(13f, 7f)
            circle(16f, 7f, 2.5f)
            moveTo(19f, 7f); lineTo(20f, 7f)
            moveTo(4f, 17f); lineTo(5f, 17f)
            circle(8f, 17f, 2.5f)
            moveTo(11f, 17f); lineTo(20f, 17f)
        }
    }
    val Reports by lazy {
        icon("Reports") {
            moveTo(7f, 3f); lineTo(14f, 3f); lineTo(19f, 8f); lineTo(19f, 21f); lineTo(7f, 21f); close()
            moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
            moveTo(10f, 13f); lineTo(16f, 13f)
            moveTo(10f, 17f); lineTo(16f, 17f)
        }
    }
    val Settings by lazy {
        icon("Settings") {
            moveTo(12f, 3f); lineTo(19.8f, 7.5f); lineTo(19.8f, 16.5f); lineTo(12f, 21f); lineTo(4.2f, 16.5f)
            lineTo(4.2f, 7.5f); close()
            circle(12f, 12f, 3f)
        }
    }
}

private fun icon(name: String, strokeWidth: Float = 1.8f, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
        .build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    close()
}

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    horizontalLineToRelative(w - 2 * r)
    arcToRelative(r, r, 0f, false, true, r, r)
    verticalLineToRelative(h - 2 * r)
    arcToRelative(r, r, 0f, false, true, -r, r)
    horizontalLineToRelative(-(w - 2 * r))
    arcToRelative(r, r, 0f, false, true, -r, -r)
    verticalLineToRelative(-(h - 2 * r))
    arcToRelative(r, r, 0f, false, true, r, -r)
    close()
}

@Preview(name = "Icons, light", widthDp = 400)
@Composable
private fun AppIconsPreview() {
    AppTheme { IconGrid() }
}

@Preview(name = "Icons, dark", widthDp = 400)
@Composable
private fun AppIconsDarkPreview() {
    AppTheme(darkTheme = true) { IconGrid() }
}

/** Every icon on a tile with its name underneath, four to a row. */
@Composable
private fun IconGrid() {
    val colors = AppTheme.colors
    Column(Modifier.background(colors.background).padding(12.dp)) {
        AppIcons.all.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (name, icon) ->
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(colors.card, RoundedCornerShape(18.dp))
                                .border(1.dp, colors.outline, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = name, tint = colors.label, modifier = Modifier.size(30.dp))
                        }
                        Text(
                            text = name,
                            style = AppTheme.typography.caption,
                            color = colors.secondaryLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
