package com.danyt.meerkat.core.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.size
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** How a device was heard: by classic discovery, by its Low Energy announcements, or both. */
enum class BluetoothKind { Classic, LowEnergy, Dual }

/**
 * One Bluetooth device in range.
 *
 * @property name the name it announces, or null when it gives none.
 * @property majorClass the kind of device it says it is (one of BluetoothClass.Device.Major), classic only.
 * @property connectable whether it accepts connections, Low Energy only; null when unknown.
 * @property txPower the transmit power it announces in dBm, or null.
 * @property manufacturers Bluetooth company identifiers found in its announcements.
 * @property services 16-bit service identifiers found in its announcements, such as 0x180F for battery.
 * @property seenAt when it was last heard, in milliseconds since the epoch.
 * @property appearance the kind of device it announces itself as (a Bluetooth appearance value), Low Energy only.
 * @property apple what an Apple device's messages say, or null for other makers.
 * @property battery its battery level in percent, when it announces one in the standard battery service.
 * @property history its signal over time, as (time, dBm) pairs, about one a second, the latest last.
 * @property addressType whether [address] is the device's own or one it made up, or null when unknown.
 */
data class BluetoothSighting(
    val address: String,
    val name: String?,
    val dbm: Int,
    val kind: BluetoothKind,
    val majorClass: Int? = null,
    val connectable: Boolean? = null,
    val txPower: Int? = null,
    val manufacturers: List<Int> = emptyList(),
    val services: List<Int> = emptyList(),
    val seenAt: Long,
    val appearance: Int? = null,
    val apple: AppleInfo? = null,
    val battery: Int? = null,
    val history: List<Pair<Long, Int>> = listOf(seenAt to dbm),
    val addressType: BluetoothAddressType? = null,
) {
    /** Combines what classic discovery and Low Energy each learned about the same address. */
    fun merge(newer: BluetoothSighting): BluetoothSighting = newer.copy(
        appearance = newer.appearance ?: appearance,
        addressType = newer.addressType ?: addressType,
        apple = mergeApple(apple, newer.apple),
        battery = newer.battery ?: battery,
        // One sample a second is enough for a chart, and keeps memory small however long the scan runs.
        history = if (newer.seenAt - (history.lastOrNull()?.first ?: 0) >= SAMPLE_MILLIS) {
            (history + (newer.seenAt to newer.dbm)).takeLast(MAX_SAMPLES)
        } else {
            history
        },
        name = newer.name ?: name,
        kind = if (newer.kind == kind) kind else BluetoothKind.Dual,
        majorClass = newer.majorClass ?: majorClass,
        connectable = newer.connectable ?: connectable,
        txPower = newer.txPower ?: txPower,
        manufacturers = (manufacturers + newer.manufacturers).distinct(),
        services = (services + newer.services).distinct(),
    )
}

/** Apple devices send different messages in turn; what any of them said is kept. */
private fun mergeApple(older: AppleInfo?, newer: AppleInfo?): AppleInfo? = when {
    older == null -> newer
    newer == null -> older
    else -> AppleInfo(
        messages = (older.messages + newer.messages).distinct(),
        model = newer.model ?: older.model,
        left = newer.left ?: older.left,
        right = newer.right ?: older.right,
        case = newer.case ?: older.case,
    )
}

private const val SAMPLE_MILLIS = 1_000L
private const val MAX_SAMPLES = 120

/**
 * What kind of address a device uses. A public address is its own, registered to its maker; a random one is
 * made up, usually for privacy, and changes from time to time.
 */
enum class BluetoothAddressType { Public, Random }

/** Why a Bluetooth scan could not run. */
enum class BluetoothFailure { Unsupported, Off, NoPermission, LowEnergyFailed }

/**
 * Finds Bluetooth devices in range: classic discovery (which sends inquiries that discoverable devices answer)
 * together with listening for Low Energy announcements.
 *
 * The app declares that it never uses Bluetooth to locate the user. In return Android 12 and newer need no
 * location permission, but they leave out some beacons (devices that exist to mark a place).
 */
class BluetoothScanner(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val location = appContext.getSystemService(LocationManager::class.java)

    /** False on devices without Bluetooth. */
    val isSupported: Boolean get() = adapter != null

    /** Whether the device can listen for Low Energy announcements. */
    val isLowEnergySupported: Boolean
        get() = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    /** Whether Bluetooth is switched on. Reading this needs no runtime permission. */
    @get:SuppressLint("MissingPermission") // Install-time BLUETOOTH on Android 11 and older; none needed later.
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    /** True when Android 11 and older would find nothing while Location is off. */
    val needsLocationOn: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            location?.let(LocationManagerCompat::isLocationEnabled) != true

    /** The runtime permissions a scan needs on this Android version. */
    val permissions: List<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(Manifest.permission.BLUETOOTH_SCAN)
            else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    /**
     * Scans until collection stops, sending each device every time it is heard. Classic discovery is restarted
     * whenever it ends by itself (after about 12 seconds), so both kinds keep being found.
     *
     * Fails with a [BluetoothScanException] when the scan cannot start. The caller checks [permissions] first.
     */
    @SuppressLint("MissingPermission") // Checked by the caller; a SecurityException is reported as NoPermission.
    fun scan(): Flow<BluetoothSighting> = callbackFlow {
        val adapter = adapter ?: throw BluetoothScanException(BluetoothFailure.Unsupported)
        if (!adapter.isEnabled) throw BluetoothScanException(BluetoothFailure.Off)
        fun add(sighting: BluetoothSighting) {
            trySend(sighting)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> classicSighting(intent)?.let(::add)
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> try {
                        adapter.startDiscovery()
                    } catch (_: SecurityException) {
                        // Permission withdrawn while scanning: Low Energy stops too, and the list stays.
                    }
                }
            }
        }
        val leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = add(leSighting(result))

            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { add(leSighting(it)) }

            override fun onScanFailed(errorCode: Int) {
                close(BluetoothScanException(BluetoothFailure.LowEnergyFailed))
            }
        }
        val scanner = adapter.bluetoothLeScanner.takeIf { isLowEnergySupported }
        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            adapter.startDiscovery()
            scanner?.startScan(null, leSettings(), leCallback)
        } catch (_: SecurityException) {
            appContext.unregisterReceiver(receiver)
            throw BluetoothScanException(BluetoothFailure.NoPermission)
        }
        awaitClose {
            try {
                appContext.unregisterReceiver(receiver)
                adapter.cancelDiscovery()
                // Stopping fails when Bluetooth was switched off meanwhile; there is nothing left to stop then.
                if (adapter.isEnabled) scanner?.stopScan(leCallback)
            } catch (_: SecurityException) {
                // The permission was withdrawn; Android has stopped the scan itself.
            } catch (_: IllegalStateException) {
                // Bluetooth went off between the check and the call.
            }
        }
    }

    private fun leSettings(): ScanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .apply {
            // Also hear the longer announcements of Bluetooth 5, where the phone's radio supports them.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && adapter?.isLeExtendedAdvertisingSupported == true) {
                setLegacy(false)
            }
        }
        .build()

    private fun classicSighting(intent: Intent): BluetoothSighting? {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return null
        val deviceClass =
            IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
        return BluetoothSighting(
            address = device.address,
            name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)?.let(::cleanName)?.ifEmpty { null },
            dbm = if (rssi == Short.MIN_VALUE) NO_SIGNAL else rssi.toInt(),
            kind = BluetoothKind.Classic,
            // Classic devices always use their own address.
            addressType = BluetoothAddressType.Public,
            majorClass = deviceClass?.majorDeviceClass,
            seenAt = System.currentTimeMillis(),
        )
    }

    /** Android 15 and newer say whether a Low Energy address is the device's own; older versions do not. */
    private fun addressType(result: ScanResult): BluetoothAddressType? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return when (result.device.addressType) {
            BluetoothDevice.ADDRESS_TYPE_PUBLIC -> BluetoothAddressType.Public
            BluetoothDevice.ADDRESS_TYPE_RANDOM -> BluetoothAddressType.Random
            else -> null
        }
    }

    private fun leSighting(result: ScanResult): BluetoothSighting {
        val record = result.scanRecord
        val makers = record?.manufacturerSpecificData
        val transmitPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
        return BluetoothSighting(
            address = result.device.address,
            name = record?.deviceName?.let(::cleanName)?.ifEmpty { null },
            dbm = result.rssi,
            kind = BluetoothKind.LowEnergy,
            connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else null,
            txPower = transmitPower,
            manufacturers = makers?.let { data -> List(data.size) { data.keyAt(it) } }.orEmpty(),
            services = record?.serviceUuids.orEmpty().mapNotNull { shortUuid(it.uuid) },
            seenAt = System.currentTimeMillis(),
            appearance = record?.bytes?.let(::appearanceOf),
            addressType = addressType(result),
            apple = makers?.get(APPLE_COMPANY)?.let(::parseApple),
            battery = record?.getServiceData(BATTERY_SERVICE)?.firstOrNull()?.toInt()?.and(0xFF)?.takeIf { it <= 100 },
        )
    }

    private companion object {
        /** Stands for "no signal reading", far below anything a radio reports. */
        const val NO_SIGNAL = -127
        const val APPLE_COMPANY = 0x004C
        val BATTERY_SERVICE: ParcelUuid = ParcelUuid.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    }
}

/** Thrown out of [BluetoothScanner.scan] when the scan cannot run. */
class BluetoothScanException(val failure: BluetoothFailure) : Exception(failure.name)

/**
 * The 16-bit form of a Bluetooth service [uuid], or null for a full 128-bit identifier of a maker's own.
 * Standard services sit in the Bluetooth base UUID 0000xxxx-0000-1000-8000-00805F9B34FB.
 */
fun shortUuid(uuid: java.util.UUID): Int? {
    if (uuid.leastSignificantBits != BASE_UUID_LOW) return null
    val high = uuid.mostSignificantBits
    if (high and 0xFFFF_FFFFL != BASE_UUID_HIGH_TAIL) return null
    val value = (high ushr 32).toInt()
    return value.takeIf { it in 0..0xFFFF }
}

private val BASE_UUID_LOW = 0x8000_0080_5F9B_34FBuL.toLong()
private const val BASE_UUID_HIGH_TAIL = 0x0000_1000L
