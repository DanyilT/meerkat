package com.danyt.meerkat.feature.reports

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.core.reports.Report
import com.danyt.meerkat.core.reports.ReportFormatException
import com.danyt.meerkat.core.reports.ReportHashes
import com.danyt.meerkat.core.reports.ReportStore
import com.danyt.meerkat.core.reports.contentHash
import com.danyt.meerkat.core.reports.reportFromJson
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What went wrong with an import, for the screen to explain. */
enum class ImportError { Unreadable, NotAReport, NotSaved }

/**
 * @property reports the saved reports, newest first, or null while they are being read.
 * @property importError set when the last import failed, until the screen has shown it.
 * @property duplicate an imported report that looks the same as one already saved, waiting for the user to
 *   decide whether to keep it as well.
 */
@Immutable
data class ReportsUiState(
    val reports: List<Report>? = null,
    val importError: ImportError? = null,
    val duplicate: Report? = null,
)

class ReportsViewModel(private val store: ReportStore) : ViewModel() {
    private val importError = MutableStateFlow<ImportError?>(null)
    private val duplicate = MutableStateFlow<Report?>(null)

    val uiState: StateFlow<ReportsUiState> = combine(store.reports, importError, duplicate, ::ReportsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState(store.reports.value))

    init {
        viewModelScope.launch { store.load() }
    }

    /** Saves a report from the text of an exported JSON file. [text] is null when the file could not be read. */
    fun import(text: String?) {
        if (text == null) {
            importError.value = ImportError.Unreadable
            return
        }
        viewModelScope.launch {
            val report = try {
                reportFromJson(text)
            } catch (_: ReportFormatException) {
                importError.value = ImportError.NotAReport
                return@launch
            }
            store.load()
            // The same fingerprint almost certainly means the same report, but the user decides.
            if (report.contentHash() in store.hashes.value.saved) duplicate.value = report else save(report)
        }
    }

    /** Answers the question about [ReportsUiState.duplicate]: keep the imported copy too, or drop it. */
    fun resolveDuplicate(keep: Boolean) {
        val report = duplicate.value ?: return
        duplicate.value = null
        if (keep) viewModelScope.launch { save(report) }
    }

    private suspend fun save(report: Report) {
        importError.value = try {
            store.save(report)
            null
        } catch (_: IOException) {
            ImportError.NotSaved
        }
    }

    fun onImportErrorShown() {
        importError.value = null
    }
}

/**
 * Shows one saved report.
 *
 * @property report the report, or null while loading and after it was deleted.
 * @property loaded false until the store has been read, so "not found" does not flash.
 * @property hashes fingerprints of saved and exported reports, for the export sheet.
 */
@Immutable
data class ReportUiState(
    val report: Report? = null,
    val loaded: Boolean = false,
    val hashes: ReportHashes = ReportHashes(),
)

class ReportViewModel(private val store: ReportStore, private val id: String) : ViewModel() {
    private val deleted = MutableStateFlow(false)

    val uiState: StateFlow<ReportUiState> = combine(store.reports, deleted, store.hashes) { reports, deleted, hashes ->
        ReportUiState(
            report = reports?.firstOrNull { it.id == id }.takeIf { !deleted },
            loaded = reports != null,
            hashes = hashes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    init {
        viewModelScope.launch { store.load() }
    }

    /** Remembers that the report with this fingerprint was exported to a file. */
    fun markExported(hash: String) {
        viewModelScope.launch { store.markExported(hash) }
    }

    /** Stores the user's own title and notes for this report. An empty [name] goes back to the scanned target. */
    fun rename(name: String, description: String) {
        val report = uiState.value.report ?: return
        viewModelScope.launch {
            try {
                val notes = description.trim().take(MAX_NOTES)
                store.update(report.copy(name = name.trim().take(MAX_NAME), description = notes))
            } catch (_: IOException) {
                // The report on screen stays as it was, which shows that the change was not kept.
            }
        }
    }

    /** Deletes this report, then calls [onDeleted]. */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            store.delete(id)
            deleted.value = true
            onDeleted()
        }
    }

    private companion object {
        const val MAX_NAME = 80
        const val MAX_NOTES = 1000
    }
}
