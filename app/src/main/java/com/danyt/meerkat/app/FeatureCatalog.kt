package com.danyt.meerkat.app

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons

/** Navigation routes that the code refers to by name. Routes of tools not built yet live only in [Features]. */
object Routes {
    const val HOME = "home"
    const val FEATURES = "features"
    const val SETTINGS = "settings"
    const val LAN = "lan"
    const val HOTSPOT = "hotspot"
    const val ARG_TARGET = "target"

    /** The LAN scanner with a target filled in. */
    fun lan(target: String) = "$LAN?$ARG_TARGET=${Uri.encode(target)}"
    const val DEVICE = "lan/device/{address}"
    const val PORTS = "ports"
    const val SSH = "ssh"
    const val REPORTS = "reports"
    const val REPORT = "reports/{id}"
    const val ARG_ID = "id"

    fun report(id: String) = "reports/$id"

    /** Name of the address argument of [DEVICE], and of the optional host argument of the port scanner. */
    const val ARG_ADDRESS = "address"
    const val ARG_HOST = "host"

    fun device(address: String) = "lan/device/$address"

    fun ports(host: String) = "$PORTS?$ARG_HOST=$host"
    const val CREDITS = "settings/credits"
    const val LICENSE = "settings/license"
    const val SCOPE = "settings/scope"
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
    Feature(Routes.LAN, R.string.feature_lan_title, R.string.feature_lan_description, AppIcons.Lan, built = true),
    Feature(
        route = Routes.PORTS,
        title = R.string.feature_ports_title,
        description = R.string.feature_ports_description,
        icon = AppIcons.Ports,
        built = true,
    ),
    Feature(
        route = Routes.HOTSPOT,
        title = R.string.feature_hotspot_title,
        description = R.string.feature_hotspot_description,
        icon = AppIcons.Hotspot,
        built = true,
    ),
    Feature(Routes.SSH, R.string.feature_ssh_title, R.string.feature_ssh_description, AppIcons.Terminal),
    Feature("traffic", R.string.feature_traffic_title, R.string.feature_traffic_description, AppIcons.Traffic),
    Feature("tools", R.string.feature_tools_title, R.string.feature_tools_description, AppIcons.Tools),
    Feature(
        route = Routes.REPORTS,
        title = R.string.feature_reports_title,
        description = R.string.feature_reports_description,
        icon = AppIcons.Reports,
        built = true,
    ),
)
