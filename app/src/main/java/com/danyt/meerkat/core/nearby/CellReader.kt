package com.danyt.meerkat.core.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.telephony.CellIdentity
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.location.LocationManagerCompat
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Mobile network technologies. */
enum class CellTech { Gsm, Wcdma, TdScdma, Cdma, Lte, Nr }

/**
 * One mobile network cell the modem can hear. Numbers the modem did not report are null.
 *
 * Which number means what depends on [tech]:
 * - [area]: location area (GSM, WCDMA, TD-SCDMA), tracking area (LTE, 5G), network ID (CDMA).
 * - [cellId]: the cell's identity within its network; for CDMA the base station ID.
 * - [code]: the code that tells nearby cells apart: BSIC (GSM), scrambling code (WCDMA), cell parameter ID
 *   (TD-SCDMA), physical cell ID (LTE, 5G).
 * - [channel]: the radio channel number: ARFCN, UARFCN, EARFCN, or NR-ARFCN.
 * - [quality]: RSRQ (LTE, 5G) or Ec/No (WCDMA), in dB. [snr]: RSSNR (LTE) or SINR (5G), in dB.
 *
 * @property registered true for the cell the device is using, false for a neighbour.
 * @property level signal from 0 (none) to 4 (great), as the system rates it.
 */
data class CellTower(
    val tech: CellTech,
    val registered: Boolean,
    val operator: String? = null,
    val mcc: String? = null,
    val mnc: String? = null,
    val area: Int? = null,
    val cellId: Long? = null,
    val code: Int? = null,
    val channel: Int? = null,
    val bands: List<Int> = emptyList(),
    val bandwidthKhz: Int? = null,
    val dbm: Int? = null,
    val level: Int = 0,
    val quality: Int? = null,
    val snr: Int? = null,
    /** Timing advance in the technology's own steps; on 5G (Android 14+) in microseconds. */
    val timingAdvance: Int? = null,
)

/**
 * Roughly how far the cell's mast is, from the timing advance: how much earlier the device must transmit so its
 * signal arrives on time. Only the cell in use reports it, on GSM, LTE, and (Android 14+) 5G. Null when unknown.
 */
fun CellTower.distanceMeters(): Int? {
    val advance = timingAdvance ?: return null
    return distanceStepMeters()?.let { advance * it }
}

/**
 * How many metres one step of timing advance stands for on this cell's technology, or null where timing advance
 * gives no distance. A distance of 0 means "closer than one step".
 */
fun CellTower.distanceStepMeters(): Int? = when (tech) {
    CellTech.Lte -> LTE_METERS_PER_STEP
    CellTech.Gsm -> GSM_METERS_PER_STEP
    CellTech.Nr -> NR_METERS_PER_MICROSECOND
    else -> null
}

// One step of timing advance is 16 basic time units on LTE (about 0.52 microseconds) and one bit period on GSM
// (about 3.7 microseconds); half the distance light covers in that time.
private const val LTE_METERS_PER_STEP = 78
private const val GSM_METERS_PER_STEP = 553

// 5G reports the advance in microseconds: light covers 300 m in one, and the signal goes there and back.
private const val NR_METERS_PER_MICROSECOND = 150

/** What the device can say about the mobile network right now. */
sealed interface CellReading {
    /**
     * The cells heard by every modem, the ones in use first. [operatorName] is the network shown in the status
     * bar. With two SIMs in use, each has its own cell in use, so two cells are marked as registered.
     */
    data class Cells(val cells: List<CellTower>, val operatorName: String?) : CellReading

    /** The device has no mobile radio. */
    data object Unsupported : CellReading

    /** Cell details need precise location access, which the app does not have. */
    data object NoPermission : CellReading

    /** Location is switched off, and Android shares no cell details then. */
    data object LocationOff : CellReading
}

/**
 * Reads the mobile network cells the device's modem hears. This only asks the modem what it already knows; it
 * sends nothing.
 *
 * Android treats cell identities as location data: they need precise location access and Location switched on.
 */
class CellReader(context: Context) {
    private val appContext = context.applicationContext
    private val telephony = appContext.getSystemService(TelephonyManager::class.java)
    private val location = appContext.getSystemService(LocationManager::class.java)

    /** False on devices without a mobile radio, such as most tablets. */
    val isSupported: Boolean
        get() = telephony != null && appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    /** The runtime permissions a reading needs. Precise location, asked together with approximate. */
    val permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Reads the cells, asking the modem for fresh values where Android allows it (Android 10 and newer). */
    @SuppressLint("MissingPermission") // A missing permission is caught and reported as NoPermission.
    suspend fun read(): CellReading {
        val manager = telephony ?: return CellReading.Unsupported
        if (!isSupported) return CellReading.Unsupported
        if (appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return CellReading.NoPermission
        }
        if (location?.let(LocationManagerCompat::isLocationEnabled) != true) return CellReading.LocationOff
        val infos = try {
            // The fresh answer comes from the default SIM's modem only. The full list covers every modem (both
            // SIMs on a dual-SIM device), so it is read afterwards and preferred.
            val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) requestUpdate(manager) else null
            manager.allCellInfo?.takeIf { it.isNotEmpty() } ?: fresh.orEmpty()
        } catch (_: SecurityException) {
            return CellReading.NoPermission
        }
        // Both modems often hear the same neighbour; show it once.
        val cells = infos.mapNotNull(::toTower)
            .sortedByDescending { it.registered }
            .distinctBy { listOf(it.tech, it.registered, it.area, it.cellId, it.code, it.channel) }
        return CellReading.Cells(cells, manager.networkOperatorName?.let(::cleanName)?.ifEmpty { null })
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private suspend fun requestUpdate(manager: TelephonyManager): List<CellInfo>? =
        withTimeoutOrNull(UPDATE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                manager.requestCellInfoUpdate(
                    executor,
                    object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                            if (continuation.isActive) continuation.resume(cellInfo)
                        }

                        override fun onError(errorCode: Int, detail: Throwable?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }
        }

    @Suppress("DEPRECATION") // The int forms of MCC and MNC are the only ones before Android 9.
    private fun toTower(info: CellInfo): CellTower? {
        val registered = info.isRegistered
        return when {
            info is CellInfoLte -> {
                val id = info.cellIdentity
                val signal = info.cellSignalStrength
                CellTower(
                    tech = CellTech.Lte,
                    registered = registered,
                    operator = operatorOf(id),
                    mcc = mccOf(id, id.mcc),
                    mnc = mncOf(id, id.mnc),
                    area = id.tac.known(),
                    cellId = id.ci.known()?.toLong(),
                    code = id.pci.known(),
                    channel = id.earfcn.known(),
                    bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.toList() else emptyList(),
                    bandwidthKhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.bandwidth.known() else null,
                    dbm = signal.dbm.known(),
                    level = signal.level,
                    quality = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) signal.rsrq.known() else null,
                    snr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) signal.rssnr.known() else null,
                    timingAdvance = signal.timingAdvance.known(),
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr -> nrTower(info)
            info is CellInfoWcdma -> {
                val id = info.cellIdentity
                val signal = info.cellSignalStrength
                CellTower(
                    tech = CellTech.Wcdma,
                    registered = registered,
                    operator = operatorOf(id),
                    mcc = mccOf(id, id.mcc),
                    mnc = mncOf(id, id.mnc),
                    area = id.lac.known(),
                    cellId = id.cid.known()?.toLong(),
                    code = id.psc.known(),
                    channel = id.uarfcn.known(),
                    dbm = signal.dbm.known(),
                    level = signal.level,
                    quality = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) signal.ecNo.known() else null,
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoTdscdma -> {
                val id = info.cellIdentity
                val signal = info.cellSignalStrength
                CellTower(
                    tech = CellTech.TdScdma,
                    registered = registered,
                    operator = operatorOf(id),
                    mcc = id.mccString,
                    mnc = id.mncString,
                    area = id.lac.known(),
                    cellId = id.cid.known()?.toLong(),
                    code = id.cpid.known(),
                    channel = id.uarfcn.known(),
                    dbm = signal.dbm.known(),
                    level = signal.level,
                )
            }
            info is CellInfoGsm -> {
                val id = info.cellIdentity
                val signal = info.cellSignalStrength
                CellTower(
                    tech = CellTech.Gsm,
                    registered = registered,
                    operator = operatorOf(id),
                    mcc = mccOf(id, id.mcc),
                    mnc = mncOf(id, id.mnc),
                    area = id.lac.known(),
                    cellId = id.cid.known()?.toLong(),
                    code = id.bsic.known(),
                    channel = id.arfcn.known(),
                    dbm = signal.dbm.known(),
                    level = signal.level,
                    timingAdvance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        signal.timingAdvance.known()
                    } else {
                        null
                    },
                )
            }
            info is CellInfoCdma -> {
                val id = info.cellIdentity
                val signal = info.cellSignalStrength
                CellTower(
                    tech = CellTech.Cdma,
                    registered = registered,
                    operator = operatorOf(id),
                    area = id.networkId.known(),
                    cellId = id.basestationId.known()?.toLong(),
                    dbm = signal.dbm.known(),
                    level = signal.level,
                )
            }
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun nrTower(info: CellInfoNr): CellTower {
        val id = info.cellIdentity as android.telephony.CellIdentityNr
        val signal = info.cellSignalStrength as CellSignalStrengthNr
        return CellTower(
            tech = CellTech.Nr,
            registered = info.isRegistered,
            operator = operatorOf(id),
            mcc = id.mccString,
            mnc = id.mncString,
            area = id.tac.known(),
            cellId = id.nci.takeIf { it != Long.MAX_VALUE },
            code = id.pci.known(),
            channel = id.nrarfcn.known(),
            bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.toList() else emptyList(),
            dbm = signal.dbm.known(),
            level = signal.level,
            quality = signal.ssRsrq.known(),
            snr = signal.ssSinr.known(),
            timingAdvance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                signal.timingAdvanceMicros.known()
            } else {
                null
            },
        )
    }

    // These take Any: before Android 9 the identity classes have no common parent, and passing one as
    // CellIdentity would fail there. CellIdentity is only looked at on versions that have it.
    private fun operatorOf(id: Any): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        (id as? CellIdentity)?.operatorAlphaLong?.toString()?.let(::cleanName)?.ifEmpty { null }
    } else {
        null
    }

    private fun mccOf(id: Any, legacy: Int): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> when (id) {
            is android.telephony.CellIdentityLte -> id.mccString
            is android.telephony.CellIdentityWcdma -> id.mccString
            is android.telephony.CellIdentityGsm -> id.mccString
            else -> null
        }
        else -> legacy.known()?.toString()?.padStart(3, '0')
    }

    private fun mncOf(id: Any, legacy: Int): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> when (id) {
            is android.telephony.CellIdentityLte -> id.mncString
            is android.telephony.CellIdentityWcdma -> id.mncString
            is android.telephony.CellIdentityGsm -> id.mncString
            else -> null
        }
        // The int form cannot tell "01" from "001"; two digits is the common case.
        else -> legacy.known()?.toString()?.padStart(2, '0')
    }

    private companion object {
        const val UPDATE_TIMEOUT_MILLIS = 3_000L
        /** Runs the answer on the thread that delivers it; resuming the reader is all it does. */
        val executor = Executor(Runnable::run)
    }
}

/** The value, or null when the modem reported it as unavailable. */
private fun Int.known(): Int? = takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE }
