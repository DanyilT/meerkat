package com.danyt.meerkat.core.net

import java.util.TreeMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanStatus { Idle, Running, Finished, Cancelled }

/**
 * A network scan as the UI sees it.
 *
 * @property target what the user asked to scan, as they typed it. Null before the first scan.
 * @property mode a name for how it scans, chosen by the caller, so results can say which mode made them.
 * @property startedAt when the scan was started, in milliseconds since 1970 UTC. Null before the first scan.
 * @property scanned how many of the [total] addresses are finished.
 * @property devices the hosts found so far, sorted by address.
 */
data class LanScanState(
    val target: String? = null,
    val status: ScanStatus = ScanStatus.Idle,
    val scanned: Int = 0,
    val total: Int = 0,
    val devices: List<Device> = emptyList(),
    val startedAt: Long? = null,
    val mode: String? = null,
)

/**
 * Runs one network scan at a time and keeps its results.
 *
 * It lives for the whole app, not for one screen, so a scan keeps running and its results stay available when
 * the user moves to another screen. Nothing is written to disk.
 */
class LanScanSession(
    private val scanner: LanScanner,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(LanScanState())

    /** The current scan, updated as results arrive. */
    val state: StateFlow<LanScanState> = _state.asStateFlow()

    private var job: Job? = null
    private val generation = AtomicInteger()

    /**
     * Starts scanning [range], replacing any earlier scan and its results. [target] is the text to show for it,
     * and [mode] a name for the settings used.
     */
    fun start(target: String, range: IpRange, config: ScanConfig, mode: String? = null) {
        job?.cancel()
        val id = generation.incrementAndGet()
        _state.value = LanScanState(
            target = target,
            status = ScanStatus.Running,
            total = range.size.toInt(),
            startedAt = clock(),
            mode = mode,
        )
        job = scope.launch {
            val found = TreeMap<Ipv4, Device>()
            scanner.scan(range, config).collect { event ->
                when (event) {
                    is ScanEvent.Progress -> update(id) { it.copy(scanned = event.scanned) }
                    is ScanEvent.Found -> {
                        found[event.device.address] = event.device
                        val devices = found.values.toList()
                        update(id) { it.copy(devices = devices) }
                    }
                }
            }
            update(id) { it.copy(status = ScanStatus.Finished) }
        }
    }

    /** Stops the running scan and keeps what it found. Does nothing when no scan is running. */
    fun cancel() {
        job?.cancel()
        update(generation.get()) { if (it.status == ScanStatus.Running) it.copy(status = ScanStatus.Cancelled) else it }
    }

    /** Applies [change] only while scan [id] is still the current one, so a replaced scan cannot write results. */
    private fun update(id: Int, change: (LanScanState) -> LanScanState) {
        _state.update { if (generation.get() == id) change(it) else it }
    }
}
