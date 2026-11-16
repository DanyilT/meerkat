package com.danyt.meerkat.core.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Blur radius shared by all glass surfaces. */
private val GlassBlurRadius = 28.dp

/**
 * Haze state of the whole app content, provided by [GlassBackdrop]. Surfaces that float above the app in their
 * own window, such as sheets, blur this. Null when there is no backdrop (previews, tests).
 */
val LocalBackdropHazeState = staticCompositionLocalOf<HazeState?> { null }

/** True when this device can blur at all: Android 12 (API 31) and newer. */
val deviceSupportsBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** True when blur should be drawn: the device supports it, the user has it on, and this is not a preview. */
@Composable
fun isBlurSupported(): Boolean = deviceSupportsBlur && AppTheme.blurEnabled && !LocalInspectionMode.current

/**
 * Marks [content] as the backdrop that floating glass surfaces blur, and exposes it as [LocalBackdropHazeState].
 * Wrap the app's root content in this once.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = rememberHazeState()
    CompositionLocalProvider(LocalBackdropHazeState provides state) {
        // Capturing content for blur has a cost, so it is skipped entirely while blur is off or unsupported.
        val source = if (isBlurSupported()) Modifier.hazeSource(state) else Modifier
        Box(modifier.fillMaxSize().then(source), content = content)
    }
}

/**
 * A translucent "glass" surface.
 *
 * With a [hazeState] on Android 12+ it blurs the content marked with `Modifier.hazeSource(hazeState)` that sits
 * behind it. Otherwise it draws a nearly opaque translucent fill, which keeps text readable without blur.
 *
 * @param shape the surface is clipped to this shape.
 * @param hazeState source of the content to blur, or null for the solid translucent fallback.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = AppTheme.colors
    val surface = if (hazeState != null && isBlurSupported()) {
        val input = remember(hazeState) { HazeInput.Sources(hazeState) }
        val style = remember(colors.glassTint, colors.background) {
            HazeBlurStyle {
                blurEnabled(true)
                blurRadius(GlassBlurRadius)
                noiseFactor(0f)
                backgroundColor(colors.background)
                colorEffects(listOf(HazeColorEffect.tint(colors.glassTint)))
            }
        }
        Modifier.clip(shape).hazeBlur(input = input, style = style)
    } else {
        Modifier.clip(shape).background(colors.glassFallback)
    }
    Box(modifier.then(surface), content = content)
}

@Preview
@Composable
private fun GlassSurfacePreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.accent).padding(24.dp)) {
            GlassSurface(Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Glass", Modifier.align(Alignment.Center), style = AppTheme.typography.headline)
            }
        }
    }
}
