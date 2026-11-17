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

/**
 * A port scan as the UI sees it.
 *
 * @property host the address being scanned. Null before the first scan.
 * @property startedAt when the scan was started, in milliseconds since 1970 UTC. Null before the first scan.
 * @property preset a name for the set of ports, chosen by the caller, so results can say which set made them.
 * @property open the open ports found so far, lowest first.
 * @property closed how many ports the host refused.
 * @property silent how many ports gave no answer: filtered, or for UDP possibly open.
 */
data class PortScanState(
    val host: Ipv4? = null,
    val protocol: Protocol = Protocol.Tcp,
    val status: ScanStatus = ScanStatus.Idle,
    val total: Int = 0,
    val open: List<PortResult> = emptyList(),
    val closed: Int = 0,
    val silent: Int = 0,
    val startedAt: Long? = null,
    val preset: String? = null,
) {
    /** How many ports are finished. */
    val scanned: Int get() = open.size + closed + silent
}

/**
 * Runs one port scan at a time and keeps its results.
 *
 * Like [LanScanSession], it lives for the whole app, so a scan survives leaving its screen. Nothing is written
 * to disk.
 */
class PortScanSession(
    private val scanner: PortScanner,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(PortScanState())

    /** The current scan, updated as results arrive. */
    val state: StateFlow<PortScanState> = _state.asStateFlow()

    private var job: Job? = null
    private val generation = AtomicInteger()

    /** Starts scanning [ports] on [host], replacing any earlier scan and its results. [preset] names the set. */
    fun start(host: Ipv4, ports: List<Int>, protocol: Protocol, preset: String? = null) {
        job?.cancel()
        val id = generation.incrementAndGet()
        _state.value = PortScanState(
            host = host,
            protocol = protocol,
            status = ScanStatus.Running,
            total = ports.size,
            startedAt = clock(),
            preset = preset,
        )
        job = scope.launch {
            val open = TreeMap<Int, PortResult>()
            scanner.scan(host, ports, protocol, TIMEOUT_MILLIS, CONCURRENCY).collect { result ->
                when (result.state) {
                    PortState.Open -> {
                        open[result.port] = result
                        val found = open.values.toList()
                        update(id) { it.copy(open = found) }
                    }
                    PortState.Closed -> update(id) { it.copy(closed = it.closed + 1) }
                    else -> update(id) { it.copy(silent = it.silent + 1) }
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

    private fun update(id: Int, change: (PortScanState) -> PortScanState) {
        _state.update { if (generation.get() == id) change(it) else it }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 500
        const val CONCURRENCY = 256
    }
}
