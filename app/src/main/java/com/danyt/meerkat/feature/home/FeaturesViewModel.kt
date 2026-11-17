package com.danyt.meerkat.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.core.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class FeaturesUiState(val items: List<HomeItem> = emptyList())

/** The list of every tool, with the "New" mark on tools that arrived recently. */
class FeaturesViewModel(items: List<HomeItem>, settingsRepository: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<FeaturesUiState> = settingsRepository.settings
        .map { settings -> FeaturesUiState(items.map { it.copy(isNew = it.built && it.route in settings.newTools) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeaturesUiState(items))
}
