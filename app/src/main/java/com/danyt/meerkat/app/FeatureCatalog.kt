package com.danyt.meerkat.app

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons

/** Navigation routes that are not tools. Tool routes live in [Features]. */
object Routes {
    const val HOME = "home"
    const val FEATURES = "features"
    const val SETTINGS = "settings"
    const val CREDITS = "settings/credits"
    const val LICENSE = "settings/license"
}

/**
 * A tool the app offers.
 *
 * @property route navigation route.
 * @property title name of the tool, as a string resource.
 * @property description one line shown on the tile, in the list of all tools, and on the "Coming soon" screen.
 * @property built false until the feature has a real screen. Unbuilt features open the placeholder.
 */
data class Feature(
    val route: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val icon: ImageVector,
    val built: Boolean = false,
)

/** Every tool, in home screen order. Flip `built` and add a destination in [AppNavHost] when one ships. */
val Features = listOf(
    Feature("lan", R.string.feature_lan_title, R.string.feature_lan_description, AppIcons.Lan),
    Feature("ports", R.string.feature_ports_title, R.string.feature_ports_description, AppIcons.Ports),
    Feature("ssh", R.string.feature_ssh_title, R.string.feature_ssh_description, AppIcons.Terminal),
    Feature("traffic", R.string.feature_traffic_title, R.string.feature_traffic_description, AppIcons.Traffic),
    Feature("tools", R.string.feature_tools_title, R.string.feature_tools_description, AppIcons.Tools),
    Feature("reports", R.string.feature_reports_title, R.string.feature_reports_description, AppIcons.Reports),
)
