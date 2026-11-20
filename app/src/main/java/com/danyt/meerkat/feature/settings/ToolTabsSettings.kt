package com.danyt.meerkat.feature.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.GroupedSwitchRow
import com.danyt.meerkat.core.design.Menu
import com.danyt.meerkat.core.design.MenuItem
import com.danyt.meerkat.core.settings.AppSettings

/** A tool with tabs, as Settings needs to know it: its route, its name, and its tabs. */
@Immutable
data class TabbedTool(val route: String, @param:StringRes val title: Int, val tabs: List<ToolTab>)

/** One tab of a [TabbedTool]. [key] is how the tool and the settings name it. */
@Immutable
data class ToolTab(val key: String, @param:StringRes val title: Int, val icon: ImageVector)

/**
 * Which tab [tool] opens on: the one used last, or a fixed one (its first tab until the user picks another).
 * A long press on the tool's tile still opens any tab directly.
 */
@Composable
internal fun ToolTabsSection(
    tool: TabbedTool,
    settings: AppSettings,
    onStartTab: (route: String, tab: String?) -> Unit,
) {
    val choice = settings.startTabs[tool.route]
    val openLast = choice == AppSettings.LAST_TAB
    val fixed = tool.tabs.firstOrNull { it.key == choice } ?: tool.tabs.first()
    val animate = AppTheme.animationsEnabled
    GroupedSection(
        header = stringResource(tool.title),
        footer = stringResource(R.string.settings_start_tab_footer),
    ) {
        GroupedSwitchRow(
            title = stringResource(R.string.settings_start_tab_last),
            checked = openLast,
            onCheckedChange = { last -> onStartTab(tool.route, if (last) AppSettings.LAST_TAB else fixed.key) },
        )
        // The fixed choice folds away while "last used" is on.
        AnimatedVisibility(
            visible = !openLast,
            enter = if (animate) expandVertically(tween(220)) + fadeIn(tween(220)) else EnterTransition.None,
            exit = if (animate) shrinkVertically(tween(200)) + fadeOut(tween(150)) else ExitTransition.None,
        ) {
            var picking by remember { mutableStateOf(false) }
            Box {
                GroupedRow(
                    title = stringResource(R.string.settings_start_tab),
                    value = stringResource(fixed.title),
                    onClick = { picking = true },
                    trailing = {
                        Icon(
                            imageVector = AppIcons.ChevronDown,
                            contentDescription = null,
                            tint = AppTheme.colors.tertiaryLabel,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )
                Menu(expanded = picking, onDismiss = { picking = false }) {
                    tool.tabs.forEach { tab ->
                        MenuItem(
                            text = stringResource(tab.title),
                            icon = tab.icon,
                            onClick = {
                                picking = false
                                onStartTab(tool.route, tab.key)
                            },
                            trailing = if (tab == fixed) {
                                { Icon(AppIcons.Check, contentDescription = null, tint = AppTheme.colors.accent) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}
