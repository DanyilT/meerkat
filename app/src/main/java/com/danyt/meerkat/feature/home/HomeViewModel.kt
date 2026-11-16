package com.danyt.meerkat.feature.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.core.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One tool, as shown on the dashboard and in the list of all tools. Text is given as string resources. */
@Immutable
data class HomeItem(
    val route: String,
    @param:StringRes val title: Int,
    @param:StringRes val subtitle: Int,
    val icon: ImageVector,
    val built: Boolean,
)

/**
 * @property tiles tools on the dashboard, in the user's order.
 * @property hidden tools that are not on the dashboard, offered for adding while editing.
 * @property loaded false until the saved layout has been read, so the empty state does not flash.
 */
@Immutable
data class HomeUiState(
    val tiles: List<HomeItem> = emptyList(),
    val hidden: List<HomeItem> = emptyList(),
    val loaded: Boolean = false,
)

class HomeViewModel(
    private val items: List<HomeItem>,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val byRoute = items.associateBy { it.route }

    val uiState: StateFlow<HomeUiState> = settingsRepository.settings
        .map { settings ->
            // Until the user customizes the dashboard, every tool is shown in catalog order.
            val tiles = settings.dashboardTiles?.mapNotNull(byRoute::get) ?: items
            HomeUiState(tiles = tiles, hidden = items - tiles.toSet(), loaded = true)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Saves a new tile order, given as routes. */
    fun setTileOrder(routes: List<String>) {
        viewModelScope.launch { settingsRepository.setDashboardTiles(routes) }
    }

    /** Adds a tool to the end of the dashboard. */
    fun addTile(route: String) = setTileOrder(uiState.value.tiles.map { it.route } - route + route)

    /** Removes a tool from the dashboard. It stays available under All Tools. */
    fun removeTile(route: String) = setTileOrder(uiState.value.tiles.map { it.route } - route)
}
