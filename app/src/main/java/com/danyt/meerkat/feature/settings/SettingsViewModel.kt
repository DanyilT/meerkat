package com.danyt.meerkat.feature.settings

import com.danyt.meerkat.core.reports.ReportStore
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.core.settings.AppSettings
import com.danyt.meerkat.core.settings.SettingsRepository
import com.danyt.meerkat.core.settings.ThemeMode
import com.danyt.meerkat.core.storage.CacheManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property cacheSizeBytes current cache size, or null while it is being measured.
 * @property clearingCache true while the cache is being deleted.
 * @property reportCount how many reports are saved, or null while they are being read.
 * @property freedBytes set after a clear to report the result once, then reset by the screen.
 */
@Immutable
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val cacheSizeBytes: Long? = null,
    val clearingCache: Boolean = false,
    val freedBytes: Long? = null,
    val reportCount: Int? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val cacheManager: CacheManager,
    private val reportStore: ReportStore,
) : ViewModel() {

    private val cacheState = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, cacheState, reportStore.reports) { settings, cache, reports ->
            cache.copy(settings = settings, reportCount = reports?.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // Latest accent picked in the sheet. Conflated so a fast drag never queues up disk writes.
    private val accentRequests = Channel<Pair<Int, Boolean>>(Channel.CONFLATED)

    init {
        refreshCacheSize()
        viewModelScope.launch { reportStore.load() }
        viewModelScope.launch {
            for ((argb, monochrome) in accentRequests) {
                settingsRepository.setAccent(argb, monochrome)
                delay(ACCENT_WRITE_INTERVAL_MILLIS)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    /** Applies the accent across the app right away. Safe to call on every drag event. */
    fun setAccent(argb: Int, monochrome: Boolean) {
        accentRequests.trySend(argb to monochrome)
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAnimationsEnabled(enabled) }
    }

    fun setBlurEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBlurEnabled(enabled) }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = cacheManager.sizeBytes()
            cacheState.update { it.copy(cacheSizeBytes = size) }
        }
    }

    fun clearCache() {
        if (cacheState.value.clearingCache) return
        cacheState.update { it.copy(clearingCache = true) }
        viewModelScope.launch {
            val freed = cacheManager.clear()
            val remaining = cacheManager.sizeBytes()
            cacheState.update { it.copy(clearingCache = false, freedBytes = freed, cacheSizeBytes = remaining) }
        }
    }

    fun setIntrosEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setIntrosEnabled(enabled) }
    }

    /** Makes every screen introduce itself once more. */
    fun resetIntros() {
        viewModelScope.launch { settingsRepository.resetIntros() }
    }

    /** Deletes every saved report. */
    fun deleteAllReports() {
        viewModelScope.launch { reportStore.deleteAll() }
    }

    fun onFreedMessageShown() {
        cacheState.update { it.copy(freedBytes = null) }
    }

    private companion object {
        const val ACCENT_WRITE_INTERVAL_MILLIS = 50L
    }
}
