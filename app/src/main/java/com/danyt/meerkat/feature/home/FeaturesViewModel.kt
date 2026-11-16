package com.danyt.meerkat.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class FeaturesUiState(val items: List<HomeItem> = emptyList())

class FeaturesViewModel(items: List<HomeItem>) : ViewModel() {
    private val _uiState = MutableStateFlow(FeaturesUiState(items))
    val uiState: StateFlow<FeaturesUiState> = _uiState.asStateFlow()
}
