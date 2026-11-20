package com.danyt.meerkat.feature.nearby

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons

/**
 * The tabs of the Nearby tool. [key] is how a route names the tab; it must not change. [title] fits the tab bar;
 * [longTitle] is used where there is room, such as the tab picker and Settings.
 */
enum class NearbyTab(
    val key: String,
    @param:StringRes val title: Int,
    val icon: ImageVector,
    @param:StringRes val longTitle: Int = title,
) {
    Wifi("wifi", R.string.nearby_tab_wifi, AppIcons.Wifi),
    Bluetooth("bluetooth", R.string.nearby_tab_bluetooth, AppIcons.Bluetooth),
    Cell("cell", R.string.nearby_tab_cell, AppIcons.Cell, longTitle = R.string.nearby_tab_cell_long),
    Nfc("nfc", R.string.nearby_tab_nfc, AppIcons.Nfc),
    ;

    companion object {
        /** The tab a route names, or Wi-Fi when it names none. */
        fun of(key: String?): NearbyTab = entries.firstOrNull { it.key == key } ?: Wifi
    }
}
