package com.danyt.meerkat.core.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/**
 * Inside a [BottomSheet], closes that sheet with its closing animation and then reports the dismissal.
 * Outside a sheet it does nothing.
 */
val LocalSheetClose = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * A modal sheet on a glass surface.
 *
 * In portrait it slides up from the bottom and is dismissed by swiping down. In landscape, where a bottom
 * sheet has no room, it becomes a panel on the right edge and is dismissed by swiping it to the right.
 * Back and tapping outside dismiss it in both. On Android 12+ it blurs the app content behind it (see
 * [GlassBackdrop]).
 *
 * Content that wants to close the sheet itself, for example after a choice was made, calls
 * [LocalSheetClose]. That plays the closing animation first, which simply removing the sheet would skip.
 *
 * @param onDismiss called when the sheet is dismissed by swipe, back, tapping outside, or [LocalSheetClose].
 * @param title optional centered title at the top of the sheet.
 * @param swipeToDismiss set false when the content handles vertical drags itself, such as a color picker.
 * @param content sheet content, laid out in a column above the navigation bar and keyboard. It scrolls only
 * when it is taller than the available space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    swipeToDismiss: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The size of the app's own window, which is also right in split screen and on foldables.
    val window = LocalWindowInfo.current.containerSize
    if (window.width > window.height) {
        SideSheet(onDismiss, title, content)
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = swipeToDismiss,
        shape = SheetShape,
        containerColor = Color.Transparent,
        contentColor = AppTheme.colors.label,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        GlassSurface(Modifier.fillMaxWidth(), shape = SheetShape, hazeState = LocalBackdropHazeState.current) {
            CompositionLocalProvider(LocalSheetClose provides close) {
                SheetContent(title, swipeToDismiss, content)
            }
        }
    }
}

@Composable
private fun SheetContent(title: String?, showGrabber: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
            if (showGrabber) {
                Box(Modifier.size(width = 36.dp, height = 5.dp).background(colors.tertiaryLabel, CircleShape))
            }
        }
        if (title != null) {
            Text(
                text = title,
                style = AppTheme.typography.headline,
                color = colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .semantics { heading() },
            )
        }
        // Scrolls only when the content is taller than the space available, for example in landscape, on a small
        // screen, or with the keyboard open. When everything fits there is nothing to scroll.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
    }
}

/** The landscape form of [BottomSheet]: a full-height panel on the right edge. */
@Composable
private fun SideSheet(onDismiss: () -> Unit, title: String?, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    val animate = AppTheme.animationsEnabled
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // How far the panel is pushed off the right edge: 0 is fully open, 1 is fully off screen.
    // A plain state, so a drag can set it directly and nothing can cancel the closing animation half way.
    var hidden by remember { mutableFloatStateOf(1f) }
    var widthPx by remember { mutableIntStateOf(0) }
    val noRipple = remember { MutableInteractionSource() }

    suspend fun slideTo(target: Float) {
        if (animate) animate(hidden, target, animationSpec = tween(SIDE_SHEET_MILLIS)) { value, _ -> hidden = value }
        hidden = target
    }

    suspend fun close() {
        slideTo(1f)
        currentOnDismiss()
    }
    LaunchedEffect(Unit) { slideTo(0f) }

    Dialog(
        onDismissRequest = { scope.launch { close() } },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = noRipple, indication = null) { scope.launch { close() } },
            contentAlignment = Alignment.CenterEnd,
        ) {
            GlassSurface(
                modifier = Modifier
                    .width(minOf(SideSheetMaxWidth, maxWidth * 0.62f))
                    .fillMaxHeight()
                    .onSizeChanged { widthPx = it.width }
                    .graphicsLayer {
                        translationX = hidden * widthPx
                        // Stay invisible until the width is known, so the panel never flashes in place before sliding.
                        alpha = if (widthPx == 0) 0f else 1f
                    }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            if (widthPx > 0) hidden = (hidden + delta / widthPx).coerceIn(0f, 1f)
                        },
                        orientation = Orientation.Horizontal,
                        onDragStopped = { velocity ->
                            if (hidden > 0.3f || velocity > 1200f) close() else slideTo(0f)
                        },
                    )
                    // Swallow taps on the panel so they do not count as "outside".
                    .clickable(interactionSource = noRipple, indication = null, onClick = {}),
                shape = SideSheetShape,
                hazeState = LocalBackdropHazeState.current,
            ) {
                Column(Modifier.fillMaxSize().imePadding().padding(top = 18.dp)) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = AppTheme.typography.headline,
                            color = colors.label,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                        )
                    }
                    CompositionLocalProvider(LocalSheetClose provides { scope.launch { close() } }) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
                    }
                }
            }
        }
    }
}

private val SideSheetShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
private val SideSheetMaxWidth = 420.dp
private const val SIDE_SHEET_MILLIS = 240

@Preview
@Composable
private fun BottomSheetPreview() {
    AppTheme {
        GlassSurface(shape = SheetShape) {
            SheetContent(title = "Accent Color", showGrabber = true) {
                PrimaryButton("Done", onClick = {}, modifier = Modifier.fillMaxWidth().padding(16.dp))
            }
        }
    }
}
