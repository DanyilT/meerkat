package com.danyt.meerkat.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import com.danyt.meerkat.core.qr.QrCode
import com.danyt.meerkat.core.qr.wifiQrText

/** Modules of light border that readers need around a QR code. */
private const val QUIET_ZONE = 4

/**
 * Draws [text] as a QR code: black on white in a light theme, white on black in a dark one. Current camera
 * apps read both; a few old scanners only read the light version.
 *
 * @param description what the code is for, read out by screen readers.
 */
@Composable
fun QrImage(text: String, description: String, modifier: Modifier = Modifier) {
    val code = remember(text) { QrCode.encode(text) }
    val background = qrBackground()
    val module = if (AppTheme.colors.isDark) Color.White else Color.Black
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .semantics { contentDescription = description },
    ) {
        // All modules form one shape, filled once, so neighbouring modules leave no faint seams between them.
        // Whole-pixel modules keep their edges sharp.
        val modules = code.size + QUIET_ZONE * 2
        val cell = floor(size.width / modules)
        val origin = floor((size.width - cell * code.size) / 2)
        val shape = Path()
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                if (code[x, y]) {
                    shape.addRect(Rect(Offset(origin + x * cell, origin + y * cell), Size(cell, cell)))
                }
            }
        }
        drawPath(shape, module)
    }
}

/** The light or dark color behind a [QrImage] in the current theme, for surfaces that should blend with it. */
@Composable
fun qrBackground(): Color = if (AppTheme.colors.isDark) Color.Black else Color.White

@Preview
@Composable
private fun QrImagePreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
            QrImage(wifiQrText("AndroidShare_1662", "wdyj3rh39nh6aya", "WPA"), "Wi-Fi QR code")
        }
    }
}
