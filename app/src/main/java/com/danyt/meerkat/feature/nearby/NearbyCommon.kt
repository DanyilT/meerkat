package com.danyt.meerkat.feature.nearby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.HeaderIconButton
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.PrimaryButton
import com.danyt.meerkat.core.design.SecondaryButton
import com.danyt.meerkat.core.design.SignalBars
import com.danyt.meerkat.core.design.animated
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

/** Side padding of everything that is not a grouped card. */
internal val NearbyInset = Modifier.padding(horizontal = 16.dp)

/** Whether a section is folded away, and how to change it. */
internal class Fold(val folded: Boolean, val onFold: (Boolean) -> Unit)

/** The chevron at the right of a section's header line that folds the section away and back. */
@Composable
internal fun FoldButton(fold: Fold) {
    HeaderIconButton(
        icon = if (fold.folded) AppIcons.ChevronDown else AppIcons.ChevronUp,
        contentDescription = stringResource(if (fold.folded) R.string.action_expand else R.string.action_collapse),
        onClick = { fold.onFold(!fold.folded) },
    )
}

/** Explains why a tab needs a permission, with a button that asks for it. */
@Composable
internal fun PermissionPrompt(text: String, onAllow: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.then(NearbyInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Note(text)
        PrimaryButton(stringResource(R.string.nearby_allow), onAllow, Modifier.fillMaxWidth())
    }
}

/** Something to switch on in the system settings, with a button that opens the right screen. */
@Composable
internal fun SettingsPrompt(text: String, button: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.then(NearbyInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Note(text)
        SecondaryButton(button, onClick, Modifier.fillMaxWidth())
    }
}

/** A signal strength in dBm with bars beside it. */
@Composable
internal fun SignalValue(dbm: Int?, level: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dbm != null) {
            Text(
                text = stringResource(R.string.nearby_dbm, dbm),
                style = AppTheme.typography.subhead,
                color = AppTheme.colors.secondaryLabel,
            )
            Spacer(Modifier.width(8.dp))
        }
        SignalBars(level)
    }
}

/**
 * A signal over time: the last [WINDOW_MILLIS] of [samples] ((time, dBm) pairs) as a line, from -100 dBm at the
 * bottom to -30 dBm at the top, with the latest value at the right edge.
 */
@Composable
internal fun SignalHistory(samples: List<Pair<Long, Int>>, now: Long, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = AppTheme.typography.caption.copy(color = colors.secondaryLabel)
    val recent = samples.filter { now - it.first <= WINDOW_MILLIS }
    val weakest = recent.minOfOrNull { it.second }
    val strongest = recent.maxOfOrNull { it.second }
    val description = if (weakest != null && strongest != null) {
        stringResource(R.string.nearby_signal_range, weakest, strongest)
    } else {
        ""
    }
    Canvas(modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = description }) {
        fun x(time: Long) = size.width * (1f - (now - time).coerceIn(0, WINDOW_MILLIS).toFloat() / WINDOW_MILLIS)
        fun y(dbm: Int) = size.height * (1f - (dbm + 100).coerceIn(0, 70) / 70f)
        listOf(-90, -70, -50).forEach { level ->
            drawLine(colors.outline, Offset(0f, y(level)), Offset(size.width, y(level)), strokeWidth = 1f)
            val label = measurer.measure("$level", labelStyle)
            drawText(label, topLeft = Offset(0f, y(level) - label.size.height))
        }
        if (recent.size >= 2) {
            val line = Path().apply {
                moveTo(x(recent.first().first), y(recent.first().second))
                recent.drop(1).forEach { (time, dbm) -> lineTo(x(time), y(dbm)) }
            }
            drawPath(line, colors.accent, style = Stroke(width = 2.dp.toPx()))
        }
        recent.lastOrNull()?.let { (time, dbm) -> drawCircle(colors.accent, 3.dp.toPx(), Offset(x(time), y(dbm))) }
    }
    if (description.isNotEmpty()) {
        Text(description, style = AppTheme.typography.caption, color = colors.secondaryLabel)
    }
}

/** How far back [SignalHistory] reaches. */
private const val WINDOW_MILLIS = 120_000L

/** The current time, updated every second while shown, for "seen 5 s ago" texts. */
@Composable
internal fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** How long ago [at] was, such as "just now", "12 s ago", or "3 min ago". */
@Composable
internal fun agoText(at: Long, now: Long): String {
    val seconds = ((now - at) / 1000).coerceAtLeast(0)
    return when {
        seconds < 2 -> stringResource(R.string.nearby_ago_now)
        seconds < 60 -> stringResource(R.string.nearby_ago_seconds, seconds)
        seconds < 3600 -> stringResource(R.string.nearby_ago_minutes, seconds / 60)
        else -> clockTime(at)
    }
}

/** A time of day in the user's format, with seconds. */
internal fun clockTime(at: Long): String = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(at))
