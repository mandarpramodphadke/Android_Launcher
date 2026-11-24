package com.ailauncher.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

/**
 * Jetpack DataStore (Preferences) backing for launcher settings and the model configuration.
 * Corrupt or unreadable files degrade to defaults instead of crashing the home screen.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val store = context.launcherDataStore

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val launcherSettings: Flow<LauncherSettings> =
        data.map { it.toLauncherSettings() }.distinctUntilChanged()

    override val modelConfig: Flow<ModelConfig> =
        data.map { it.toModelConfig() }.distinctUntilChanged()

    override suspend fun updateLauncherSettings(transform: (LauncherSettings) -> LauncherSettings) {
        store.edit { prefs -> prefs.writeLauncherSettings(transform(prefs.toLauncherSettings())) }
    }

    override suspend fun updateModelConfig(transform: (ModelConfig) -> ModelConfig) {
        store.edit { prefs -> prefs.writeModelConfig(transform(prefs.toModelConfig())) }
    }
}
