package com.danyt.meerkat.core.help

import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.AppIcons
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.InfoDialog
import com.danyt.meerkat.core.design.InfoHeading
import com.danyt.meerkat.core.design.InfoItem
import com.danyt.meerkat.core.design.InfoText
import com.danyt.meerkat.core.net.Protocol
import com.danyt.meerkat.core.net.commonPorts
import com.danyt.meerkat.core.net.serviceName

/** Explains how to type a port list, what the port ranges are for, and lists the common ports of [protocol]. */
@Composable
fun PortsHelpDialog(protocol: Protocol, onDismiss: () -> Unit) {
    InfoDialog(title = stringResource(R.string.help_ports_title), onDismiss = onDismiss) {
        InfoText(stringResource(R.string.help_ports_format))
        InfoHeading(stringResource(R.string.help_ports_ranges))
        InfoItem("1-1023", stringResource(R.string.help_ports_range_well_known), labelWidth = RangeWidth)
        InfoItem("1024-49151", stringResource(R.string.help_ports_range_registered), labelWidth = RangeWidth)
        InfoItem("49152-65535", stringResource(R.string.help_ports_range_dynamic), labelWidth = RangeWidth)
        // The list is long, so it starts folded.
        var expanded by rememberSaveable { mutableStateOf(false) }
        val animate = AppTheme.animationsEnabled
        val udp = protocol == Protocol.Udp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoHeading(
                text = stringResource(if (udp) R.string.help_ports_common_udp else R.string.help_ports_common_tcp),
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            Icon(
                imageVector = if (expanded) AppIcons.ChevronUp else AppIcons.ChevronDown,
                contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                tint = AppTheme.colors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (animate) expandVertically(tween(220)) + fadeIn(tween(220)) else EnterTransition.None,
            exit = if (animate) shrinkVertically(tween(200)) + fadeOut(tween(150)) else ExitTransition.None,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoText(stringResource(R.string.help_ports_guess))
                commonPorts(protocol).forEach { port ->
                    InfoItem(port.toString(), serviceName(port, protocol).orEmpty())
                }
            }
        }
    }
}

private val RangeWidth = 100.dp
