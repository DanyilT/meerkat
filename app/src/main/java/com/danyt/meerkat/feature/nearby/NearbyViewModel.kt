package com.danyt.meerkat.feature.nearby

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyt.meerkat.core.makers.MakerList
import com.danyt.meerkat.core.makers.MakerLookup
import com.danyt.meerkat.core.nearby.BluetoothFailure
import com.danyt.meerkat.core.nearby.BluetoothScanException
import com.danyt.meerkat.core.nearby.BluetoothScanner
import com.danyt.meerkat.core.nearby.BluetoothSighting
import com.danyt.meerkat.core.nearby.CellReader
import com.danyt.meerkat.core.nearby.CellReading
import com.danyt.meerkat.core.nearby.NfcFact
import com.danyt.meerkat.core.nearby.NfcNote
import com.danyt.meerkat.core.nearby.NfcReader
import com.danyt.meerkat.core.nearby.NfcTag
import com.danyt.meerkat.core.nearby.WifiBand
import com.danyt.meerkat.core.nearby.WifiConnection
import com.danyt.meerkat.core.nearby.WifiNetwork
import com.danyt.meerkat.core.nearby.WifiScanner
import com.danyt.meerkat.core.permission.hasPermission
import com.danyt.meerkat.core.reports.Report
import com.danyt.meerkat.core.reports.ReportHashes
import com.danyt.meerkat.core.reports.ReportStore
import com.danyt.meerkat.core.settings.AppSettings
import com.danyt.meerkat.core.settings.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Nearby tool: what the device's radios can sense around it. Each tab has its own controller, and every
 * radio is used only while its tab is in front (see the `watch` functions), so nothing scans in the background.
 */
class NearbyViewModel(
    context: Context,
    wifiScanner: WifiScanner,
    bluetoothScanner: BluetoothScanner,
    cellReader: CellReader,
    nfcReader: NfcReader,
    private val reportStore: ReportStore,
    private val settingsRepository: SettingsRepository,
    initial: AppSettings? = null,
    makerList: MakerList? = null,
) : ViewModel() {
    private val appContext = context.applicationContext

    val wifi = WifiController(wifiScanner, appContext, viewModelScope)
    val bluetooth = BluetoothController(bluetoothScanner, appContext, viewModelScope)
    val cell = CellController(cellReader)
    val nfc = NfcController(nfcReader)

    /** The channel chart's remembered choices: the band the user picked (null for none) and whether it is open. */
    val chartBand: StateFlow<WifiBand?> = settingsRepository.settings
        .map { settings -> chartBandOf(settings) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initial?.let(::chartBandOf))
    /** Keys of the sections folded away (see [NearbySections]). */
    val folded: StateFlow<Set<String>> = settingsRepository.settings
        .map { it.foldedSections }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initial?.foldedSections.orEmpty())

    private fun chartBandOf(settings: AppSettings) =
        WifiBand.entries.firstOrNull { it.name == settings.channelChartBand }

    /** Whether the Bluetooth filter applies to the distance map too; remembered. */
    fun setBluetoothFilterOnMap(apply: Boolean) {
        bluetooth.setFilterOnMap(apply)
        viewModelScope.launch { settingsRepository.setBluetoothFilterOnMap(apply) }
    }

    /** Changes the Wi-Fi filter and saves it, so it is there next time. */
    fun setWifiFilter(filter: WifiFilter) {
        wifi.setFilter(filter)
        // Typing in the search field changes the filter on every key; it is saved once the typing pauses.
        filterSave?.cancel()
        filterSave = viewModelScope.launch {
            delay(FILTER_SAVE_DELAY_MILLIS)
            settingsRepository.setWifiFilter(filter.encode())
        }
    }

    private var filterSave: Job? = null

    /** Remembers the band the user picked on the chart. */
    fun pickChartBand(band: WifiBand) {
        viewModelScope.launch { settingsRepository.setChannelChartBand(band.name) }
    }

    fun setFolded(key: String, folded: Boolean) {
        viewModelScope.launch { settingsRepository.setSectionFolded(key, folded) }
    }

    /** The downloaded maker list, to name who registered an address; null when there is none. */
    val makers: StateFlow<MakerLookup?> = makerList?.lookup ?: MutableStateFlow(null)

    /** Fingerprints of saved and exported reports, for spotting copies. */
    val reportHashes: StateFlow<ReportHashes> = reportStore.hashes

    init {
        // The settings as they were when the screen opened, so the first frame already has the saved choices.
        initial?.let {
            wifi.setFilter(WifiFilter.decode(it.wifiFilter))
            bluetooth.setHideUnnamed(it.hideUnnamedBluetooth)
            bluetooth.setFilterOnMap(it.bluetoothFilterOnMap)
            nfc.setCardDetailsAllowed(it.showCardDetails)
        }
        viewModelScope.launch { reportStore.load() }
        makerList?.let { viewModelScope.launch { it.load() } }
        viewModelScope.launch {
            var first = initial == null
            settingsRepository.settings.collect {
                // Opened before the settings were read (such as after Android restored the app): the saved
                // filter choices are applied once now. Later they change only through this screen.
                if (first) {
                    wifi.setFilter(WifiFilter.decode(it.wifiFilter))
                    bluetooth.setFilterOnMap(it.bluetoothFilterOnMap)
                    first = false
                }
                bluetooth.setHideUnnamed(it.hideUnnamedBluetooth)
                nfc.setCardDetailsAllowed(it.showCardDetails)
            }
        }
    }

    /** Leaves Bluetooth devices without a name out of the list, and remembers the choice. */
    fun setHideUnnamed(hide: Boolean) {
        bluetooth.setHideUnnamed(hide)
        viewModelScope.launch { settingsRepository.setHideUnnamedBluetooth(hide) }
    }

    /** Saves [report] in the app, then gives [onResult] the stored copy, or null when storing failed. */
    fun saveReport(report: Report, onResult: (stored: Report?) -> Unit) {
        viewModelScope.launch {
            val stored = try {
                reportStore.save(report)
            } catch (_: IOException) {
                null
            }
            onResult(stored)
        }
    }

    /** Remembers that the report with this fingerprint was exported to a file. */
    fun markExported(hash: String) {
        viewModelScope.launch { reportStore.markExported(hash) }
    }

    private companion object {
        const val FILTER_SAVE_DELAY_MILLIS = 500L
    }
}

/**
 * @property permitted whether the app may read scan results.
 * @property canScan whether Wi-Fi, or the system's background Wi-Fi scanning, is on.
 * @property locationNeeded true when Android hides results until Location is switched on.
 * @property scanPermitted whether the app may start scans, which needs location access (see
 *   [WifiScanner.scanPermissions]). Without it, the list still shows results of the system's own scans.
 * @property scanning true from a scan request until its results arrive.
 * @property refused true when Android turned the user's last scan request down, because of its scan limit.
 * @property updatedAt when the shown results arrived, or null before any did.
 * @property filter which access points the list shows; see [shown].
 * @property connection the network this device is connected to, or null.
 */
@Immutable
data class WifiTabState(
    val supported: Boolean = true,
    val permitted: Boolean = false,
    val scanPermitted: Boolean = false,
    val canScan: Boolean = true,
    val locationNeeded: Boolean = false,
    val networks: List<WifiNetwork> = emptyList(),
    val scanning: Boolean = false,
    val refused: Boolean = false,
    val updatedAt: Long? = null,
    val filter: WifiFilter = WifiFilter(),
    val connection: WifiConnection? = null,
) {
    /** The access points that pass the filter, strongest first. */
    val shown: List<WifiNetwork> get() = if (filter.isActive) networks.filter(filter::matches) else networks
}

class WifiController(
    private val scanner: WifiScanner,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WifiTabState())
    val state: StateFlow<WifiTabState> = _state.asStateFlow()
    private var scanTimeout: Job? = null

    // Android allows about 4 scans in 2 minutes. The automatic scan on opening the tab takes at most one of them,
    // and none right after another scan, so the user's own taps are not turned down.
    private var lastScanAt = 0L
    private var lastAutoScanAt = 0L

    init {
        // Read before the first frame, so the tab opens with the right prompt and the networks Android already
        // knows, instead of an empty list that fills a moment later.
        refreshStatus()
        if (_state.value.permitted) show(scanner.currentResults(), fresh = false)
    }

    /** The runtime permissions to ask for before results can be read. */
    val permissions: List<String> get() = scanner.permissions

    /** Changes which access points the list shows. Kept while the tool is open. */
    fun setFilter(filter: WifiFilter) = _state.update { it.copy(filter = filter) }

    /** The runtime permissions to ask for before scans can be started. */
    val scanPermissions: List<String> get() = scanner.scanPermissions

    /** Shows results as Android delivers them, until cancelled. Call while the Wi-Fi tab is in front. */
    suspend fun watch() {
        while (true) {
            refreshStatus()
            if (_state.value.permitted) break
            delay(STATUS_POLL_MILLIS)
        }
        // Opening the tab starts a scan, as if the user had tapped Scan, unless one ran a short while ago. A
        // refusal here is not reported: the user did not ask, and the list still shows the latest results.
        val now = System.currentTimeMillis()
        val due = now - lastAutoScanAt >= AUTO_SCAN_GAP_MILLIS && now - lastScanAt >= RECENT_SCAN_MILLIS
        if (_state.value.scanPermitted && !_state.value.scanning && due) {
            lastAutoScanAt = now
            scan(byUser = false)
        }
        coroutineScope {
            // Joining or leaving a network does not bring new scan results, so the connection is read on its own.
            launch {
                while (true) {
                    delay(CONNECTION_POLL_MILLIS)
                    val connection = scanner.connection()
                    _state.update { if (it.connection == connection) it else it.copy(connection = connection) }
                }
            }
            scanner.updates().collect { update ->
                refreshStatus()
                show(update.networks, update.fresh)
            }
        }
    }

    private fun show(found: List<WifiNetwork>, fresh: Boolean) {
        val now = System.currentTimeMillis()
        _state.update { state ->
            state.copy(
                networks = found.sortedByDescending { it.dbm },
                scanning = state.scanning && !fresh,
                // New results from another scan (the system's, or another app's) make the refusal note old news.
                refused = state.refused && !fresh,
                // Older results (from before the last request) keep the time they were heard, not this moment.
                updatedAt = when {
                    found.isEmpty() -> state.updatedAt
                    fresh -> now
                    else -> found.maxOf { it.seenAt }.coerceAtMost(now)
                },
            )
        }
    }

    /**
     * Asks Android for a fresh scan. Needs [scanPermissions]; the screen asks for them first. [byUser] is false
     * for the scan that opening the tab starts, whose refusal is not shown.
     */
    fun scan(byUser: Boolean = true) {
        refreshStatus()
        if (!_state.value.scanPermitted) return
        lastScanAt = System.currentTimeMillis()
        val started = scanner.startScan()
        _state.update { it.copy(scanning = started, refused = !started && byUser) }
        scanTimeout?.cancel()
        // A scan normally takes a few seconds. Should its answer never come, stop showing it as running.
        if (started) {
            scanTimeout = scope.launch {
                delay(SCAN_TIMEOUT_MILLIS)
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    /** Reads again whether the app may scan, for example after the user answered a permission prompt. */
    fun refreshStatus() {
        _state.update {
            it.copy(
                supported = scanner.isSupported,
                permitted = context.hasPermission(scanner.readPermission),
                scanPermitted = context.hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION),
                canScan = scanner.canScan,
                connection = scanner.connection(),
                locationNeeded = scanner.needsLocationOn,
            )
        }
    }

    private companion object {
        const val SCAN_TIMEOUT_MILLIS = 20_000L
        const val STATUS_POLL_MILLIS = 1_000L
        const val CONNECTION_POLL_MILLIS = 2_000L
        const val AUTO_SCAN_GAP_MILLIS = 120_000L
        const val RECENT_SCAN_MILLIS = 30_000L
    }
}

/**
 * @property enabled whether Bluetooth is switched on.
 * @property scanning whether a scan is running.
 * @property startedAt when the running or last scan began, or null before the first.
 * @property failure why the last scan stopped early, or null.
 * @property hideUnnamed whether devices that announce no name are left out of [shown].
 * @property filterOnMap whether the distance map shows [shown] instead of every device.
 */
@Immutable
data class BluetoothTabState(
    val supported: Boolean = true,
    val permitted: Boolean = false,
    val enabled: Boolean = false,
    val locationNeeded: Boolean = false,
    val devices: List<BluetoothSighting> = emptyList(),
    val scanning: Boolean = false,
    val startedAt: Long? = null,
    val failure: BluetoothFailure? = null,
    val hideUnnamed: Boolean = false,
    val filterOnMap: Boolean = false,
) {
    /** The devices the list shows. */
    val shown: List<BluetoothSighting> get() = if (hideUnnamed) devices.filter { it.name != null } else devices

    /** The devices the distance map shows. */
    val onMap: List<BluetoothSighting> get() = if (filterOnMap) shown else devices
}

/**
 * Scans for Bluetooth devices while the tab is in front. Opening the tab starts a scan when nothing has been
 * found yet; after that, scanning keeps going until the user stops it, and resumes when they come back to the tab.
 */
class BluetoothController(
    private val scanner: BluetoothScanner,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(BluetoothTabState())
    val state: StateFlow<BluetoothTabState> = _state.asStateFlow()
    private var job: Job? = null

    /** Every device heard while the tool is open, by address. Kept across scans. */
    private val found = LinkedHashMap<String, BluetoothSighting>()

    /** True once the user pressed Stop, until they start again. Leaving the tab does not count. */
    private var stoppedByUser = false

    init {
        // Read before the first frame, so the tab opens with the right prompt instead of switching to it.
        refreshStatus()
    }

    val permissions: List<String> get() = scanner.permissions

    /**
     * Keeps the status current while the Bluetooth tab is in front, scans unless the user stopped it, and stops
     * scanning when the tab leaves.
     */
    suspend fun watch() {
        if (_state.value.devices.isEmpty()) stoppedByUser = false
        try {
            while (true) {
                refreshStatus()
                val state = _state.value
                when {
                    // Bluetooth switched off from the quick settings ends the scan.
                    !state.enabled && state.scanning -> stop()
                    // Off and no permission clear up once Bluetooth is on and allowed; other failures wait for Scan.
                    state.permitted && state.enabled && !state.scanning && !stoppedByUser &&
                        (state.failure == null || state.failure in RESOLVED_FAILURES) -> start()
                }
                delay(STATUS_POLL_MILLIS)
            }
        } finally {
            stop()
        }
    }

    /** Starts scanning, or keeps scanning, after the user asked for it. */
    fun startByUser() {
        stoppedByUser = false
        start()
    }

    /** Stops scanning because the user asked; it stays stopped until they start it again. */
    fun stopByUser() {
        stoppedByUser = true
        stop()
    }

    private fun start() {
        refreshStatus()
        job?.cancel()
        _state.update { it.copy(scanning = true, failure = null, startedAt = System.currentTimeMillis()) }
        job = scope.launch {
            // Many devices announce themselves ten times a second; a few list updates a second are enough.
            val publisher = launch {
                while (true) {
                    delay(LIST_UPDATE_MILLIS)
                    publish()
                }
            }
            scanner.scan()
                .buffer(SIGHTING_BUFFER)
                .catch { error ->
                    val failure = (error as? BluetoothScanException)?.failure ?: BluetoothFailure.LowEnergyFailed
                    _state.update { it.copy(failure = failure) }
                }
                .collect { sighting ->
                    synchronized(found) {
                        found[sighting.address] = found[sighting.address]?.merge(sighting) ?: sighting
                    }
                }
            publisher.cancel()
            publish()
            _state.update { it.copy(scanning = false) }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        publish()
        _state.update { it.copy(scanning = false) }
    }

    private fun publish() {
        val devices = synchronized(found) { found.values.toList() }
        _state.update { it.copy(devices = devices.sortedByDescending { device -> device.dbm }) }
    }

    /** Leaves devices without a name out of the list, or shows them again. */
    fun setHideUnnamed(hide: Boolean) = _state.update { it.copy(hideUnnamed = hide) }

    /** Whether the filter applies to the distance map too. */
    fun setFilterOnMap(apply: Boolean) = _state.update { it.copy(filterOnMap = apply) }

    fun refreshStatus() {
        _state.update {
            it.copy(
                supported = scanner.isSupported,
                permitted = scanner.permissions.any(context::hasPermission),
                enabled = scanner.isEnabled,
                locationNeeded = scanner.needsLocationOn,
            )
        }
    }

    private companion object {
        const val STATUS_POLL_MILLIS = 1_000L
        const val LIST_UPDATE_MILLIS = 400L

        // Failures that clear up by themselves once Bluetooth is switched on and allowed.
        val RESOLVED_FAILURES = setOf(BluetoothFailure.Off, BluetoothFailure.NoPermission)
        const val SIGHTING_BUFFER = 512
    }
}

/**
 * @property reading the last answer, or null before the first.
 * @property updatedAt when it was read.
 */
@Immutable
data class CellTabState(val reading: CellReading? = null, val updatedAt: Long? = null)

class CellController(private val reader: CellReader) {
    private val _state = MutableStateFlow(CellTabState())
    val state: StateFlow<CellTabState> = _state.asStateFlow()

    val permissions: List<String> get() = reader.permissions

    /** Reads the cells every few seconds while the tab is in front. Asking the modem sends nothing. */
    suspend fun watch() {
        while (true) {
            refresh()
            delay(REFRESH_MILLIS)
        }
    }

    suspend fun refresh() {
        val reading = withContext(Dispatchers.IO) { reader.read() }
        _state.value = CellTabState(reading, System.currentTimeMillis())
    }

    private companion object {
        const val REFRESH_MILLIS = 5_000L
    }
}

/**
 * @property tags tags read while the tool was open, newest first. Kept in memory only.
 * @property cardDetailsAllowed whether bank card numbers and expiry dates are read and shown (a setting).
 * @property rereading the read time of the tag whose entry the next bank card read replaces, or null.
 */
@Immutable
data class NfcTabState(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val reading: Boolean = false,
    val tags: List<NfcTag> = emptyList(),
    val cardDetailsAllowed: Boolean = false,
    val rereading: Long? = null,
)

class NfcController(private val reader: NfcReader) {
    // Read before the first frame, so the tab does not flash "NFC is off" on a device without NFC.
    private val _state = MutableStateFlow(NfcTabState(supported = reader.isSupported, enabled = reader.isEnabled))
    val state: StateFlow<NfcTabState> = _state.asStateFlow()

    /**
     * Reads tags held to the device while the tab is in front of [activity]. NFC can be switched on and off at
     * any time, so the switch is checked every second and reading starts once it is on.
     */
    suspend fun watch(activity: Activity) {
        var started = false
        try {
            while (true) {
                val enabled = reader.isEnabled
                if (enabled && !started) {
                    reader.start(activity, readCardDetails = { _state.value.cardDetailsAllowed }, onTag = ::add)
                } else if (!enabled && started) {
                    reader.stop(activity)
                }
                started = enabled
                _state.update { it.copy(supported = reader.isSupported, enabled = enabled, reading = enabled) }
                delay(STATUS_POLL_MILLIS)
            }
        } finally {
            if (started) reader.stop(activity)
            _state.update { it.copy(reading = false) }
        }
    }

    /**
     * A new tag goes to the top, unless it is the bank card the user asked to read again: then it replaces that
     * entry. Anything that may be another card gets its own entry, so details never move from one card to another,
     * and the waiting goes on for the card asked for.
     */
    private fun add(tag: NfcTag) = _state.update { state ->
        val old = state.rereading?.let { target -> state.tags.firstOrNull { it.readAt == target } }
        if (old != null && NfcNote.PaymentCard in tag.notes && sameCard(old, tag)) {
            state.copy(tags = state.tags.map { if (it === old) tag else it }, rereading = null)
        } else {
            state.copy(tags = (listOf(tag) + state.tags).take(MAX_TAGS))
        }
    }

    /**
     * Whether [new] is the card read as [old]. Bank cards often show a new ID each time, so the number decides;
     * without one on both sides, the payment network and card name. A read that got no number cannot be matched
     * to an entry that has one, so the details read before stay where they are.
     */
    private fun sameCard(old: NfcTag, new: NfcTag): Boolean {
        val oldNumber = old.card?.number
        val newNumber = new.card?.number
        return when {
            oldNumber != null && newNumber != null -> oldNumber == newNumber
            oldNumber != null -> false
            else -> old.paymentIdentity() == new.paymentIdentity()
        }
    }

    private fun NfcTag.paymentIdentity() =
        facts.filter { it.first == NfcFact.PaymentNetwork || it.first == NfcFact.CardName }

    /** Waits for the bank card read at [readAt] to be held to the device again, to update its entry. */
    fun reread(readAt: Long?) = _state.update { it.copy(rereading = readAt) }

    /** Whether bank card details are read and shown; follows the setting. */
    fun setCardDetailsAllowed(allowed: Boolean) = _state.update { state ->
        if (allowed) {
            state.copy(cardDetailsAllowed = true)
        } else {
            // Turned off: the numbers read so far are forgotten, not just hidden.
            state.copy(
                cardDetailsAllowed = false,
                tags = state.tags.map { it.copy(card = null, cardProblem = null) },
                rereading = null,
            )
        }
    }

    /** Forgets the tags read so far. */
    fun clear() = _state.update { it.copy(tags = emptyList(), rereading = null) }

    private companion object {
        const val MAX_TAGS = 20
        const val STATUS_POLL_MILLIS = 1_000L
    }
}

/** Keys of the Nearby sections that can be folded away. They are stored, so they must not change. */
object NearbySections {
    const val CHANNELS = "nearby.wifi.channels"
    const val BLUETOOTH_MAP = "nearby.bluetooth.map"
    const val CELL_MAP = "nearby.cell.map"
}
