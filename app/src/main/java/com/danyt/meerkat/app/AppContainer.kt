package com.danyt.meerkat.app

import java.io.IOException
import android.os.storage.StorageManager
import android.os.Process
import android.os.Build
import android.app.usage.StorageStatsManager
import com.danyt.meerkat.core.net.LocalHotspot
import com.danyt.meerkat.core.help.Intros
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.danyt.meerkat.core.net.LanScanSession
import com.danyt.meerkat.core.net.LanScanner
import com.danyt.meerkat.core.net.LocalNetwork
import com.danyt.meerkat.core.net.NetworkNameResolver
import com.danyt.meerkat.core.net.PortScanSession
import com.danyt.meerkat.core.net.PortScanner
import com.danyt.meerkat.core.net.SocketProber
import com.danyt.meerkat.core.reports.ReportStore
import com.danyt.meerkat.core.settings.SettingsRepository
import com.danyt.meerkat.core.storage.AesGcmSealer
import com.danyt.meerkat.core.storage.CacheManager
import com.danyt.meerkat.core.storage.keystoreKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container. Creates app-wide singletons lazily, on first use. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") })
    }

    /** Runs work that must outlive a single screen, such as a scan in progress. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val intros: Intros by lazy { Intros(settingsRepository, appScope) }

    val localNetwork: LocalNetwork by lazy { LocalNetwork(appContext) }

    /** The app's own local-only hotspot. Kept here so it stays up while the user moves between screens. */
    val localHotspot: LocalHotspot by lazy { LocalHotspot(appContext) }

    val lanScanSession: LanScanSession by lazy {
        LanScanSession(LanScanner(SocketProber(), NetworkNameResolver()), appScope)
    }

    val portScanSession: PortScanSession by lazy { PortScanSession(PortScanner(SocketProber()), appScope) }

    /** Saved reports, encrypted with a key that never leaves the Android Keystore. */
    val reportStore: ReportStore by lazy {
        // Looked up on first use, then kept: reading the Keystore on every save or load would be wasted work.
        val key by lazy { keystoreKey("meerkat_reports") }
        ReportStore(File(appContext.filesDir, "reports"), AesGcmSealer { key })
    }

    val cacheManager: CacheManager by lazy {
        // Both storage areas: the normal one, and the device-protected one that also works before the first
        // unlock, where Android keeps the graphics shader cache. Code caches are rebuilt by the system on demand.
        val deviceProtected = appContext.createDeviceProtectedStorageContext()
        val dirs = listOfNotNull(
            appContext.cacheDir,
            appContext.codeCacheDir,
            appContext.externalCacheDir,
            deviceProtected.cacheDir,
            deviceProtected.codeCacheDir,
        ).distinct()
        CacheManager(dirs, systemSize = ::systemCacheBytes)
    }

    /** The app's cache size as Android counts it for the app's page in system settings. Android 8 and newer. */
    private fun systemCacheBytes(): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            // Asking about the app's own user ID needs no permission.
            appContext.getSystemService(StorageStatsManager::class.java)
                ?.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid())
                ?.cacheBytes
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
