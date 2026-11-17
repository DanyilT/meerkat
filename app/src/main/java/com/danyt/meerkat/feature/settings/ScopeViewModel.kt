package com.danyt.meerkat.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.core.net.LocalNetwork
import com.danyt.meerkat.core.net.parseScopeRange
import com.danyt.meerkat.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property ranges the authorized ranges, as the user typed them.
 * @property own the networks that are always allowed right now, in CIDR form.
 * @property input the text in the "add" field.
 * @property inputError true when [input] was refused on the last attempt to add it.
 */
@Immutable
data class ScopeUiState(
    val ranges: List<String> = emptyList(),
    val own: List<String> = emptyList(),
    val input: String = "",
    val inputError: Boolean = false,
)

class ScopeViewModel(
    private val settingsRepository: SettingsRepository,
    private val localNetwork: LocalNetwork,
) : ViewModel() {

    private val form = MutableStateFlow(ScopeUiState(own = ownNetworks()))

    val uiState: StateFlow<ScopeUiState> = combine(form, settingsRepository.settings) { form, settings ->
        form.copy(ranges = settings.authorizedRanges)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    fun setInput(text: String) = form.update { it.copy(input = text, inputError = false) }

    /** Adds the typed range when it is valid, and clears the field. Otherwise marks the field as wrong. */
    fun add() {
        val text = form.value.input.trim()
        if (parseScopeRange(text) == null) {
            form.update { it.copy(inputError = true) }
            return
        }
        form.update { it.copy(input = "") }
        viewModelScope.launch { settingsRepository.setAuthorizedRanges(uiState.value.ranges + text) }
    }

    fun remove(range: String) {
        viewModelScope.launch { settingsRepository.setAuthorizedRanges(uiState.value.ranges - range) }
    }

    /** Reads the phone's own networks again, in case it changed network while the app was in the background. */
    fun refreshNetworks() = form.update { it.copy(own = ownNetworks()) }

    private fun ownNetworks() = localNetwork.snapshot().let { listOfNotNull(it.current?.cidr, it.hotspot?.cidr) }
}
