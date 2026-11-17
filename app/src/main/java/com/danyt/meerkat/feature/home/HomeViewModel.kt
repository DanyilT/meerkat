package com.danyt.meerkat.feature.home

import com.danyt.meerkat.core.settings.ThemeMode
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
    val isNew: Boolean = false,
)

/**
 * @property tiles tools on the dashboard, in the user's order.
 * @property hidden tools that are not on the dashboard, offered for adding while editing.
 * @property themeMode the current theme choice, for the appearance part of the introduction.
 * @property accentColor the current accent as an ARGB integer, or null in monochrome.
 * @property loaded false until the saved layout has been read, so the empty state does not flash.
 */
@Immutable
data class HomeUiState(
    val tiles: List<HomeItem> = emptyList(),
    val hidden: List<HomeItem> = emptyList(),
    val loaded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val accentColor: Int? = null,
)

class HomeViewModel(
    private val items: List<HomeItem>,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = settingsRepository.settings
        .map { settings ->
            // Until the user customizes the dashboard, every tool is shown in catalog order.
            val all = items.map { it.copy(isNew = it.built && it.route in settings.newTools) }
            val byRoute = all.associateBy { it.route }
            val tiles = settings.dashboardTiles?.mapNotNull(byRoute::get) ?: all
            HomeUiState(
                tiles = tiles,
                hidden = all - tiles.toSet(),
                loaded = true,
                themeMode = settings.themeMode,
                accentColor = settings.accentColor.takeIf { !settings.monochrome },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Saves a new tile order, given as routes. */
    fun setTileOrder(routes: List<String>) {
        viewModelScope.launch { settingsRepository.setDashboardTiles(routes) }
    }

    init {
        // Put tools that arrived with an update on the dashboard before the user sees it.
        viewModelScope.launch { settingsRepository.reconcileTools(items.filter { it.built }.map { it.route }) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    /** Sets one of the preset accent colors, given as an ARGB integer. */
    fun setAccent(argb: Int) {
        viewModelScope.launch { settingsRepository.setAccent(argb, monochrome = false) }
    }

    /** Adds a tool to the end of the dashboard. */
    fun addTile(route: String) = setTileOrder(uiState.value.tiles.map { it.route } - route + route)

    /** Removes a tool from the dashboard. It stays available under All Tools. */
    fun removeTile(route: String) = setTileOrder(uiState.value.tiles.map { it.route } - route)
}
