package com.danyt.meerkat.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.danyt.meerkat.core.settings.SettingsRepository
import com.danyt.meerkat.core.storage.CacheManager

/** Manual dependency container. Creates app-wide singletons lazily, on first use. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") })
    }

    val cacheManager: CacheManager by lazy {
        CacheManager(listOfNotNull(appContext.cacheDir, appContext.externalCacheDir))
    }
}
