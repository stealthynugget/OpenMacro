package dev.openmacro.core.engine

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "engine_settings")

/** App-wide preferences backed by Jetpack DataStore. */
class EngineSettings(context: Context) {
    private val store = context.applicationContext.settingsStore

    /** Master switch: when off, no events are dispatched and the daemon is stopped. */
    val engineEnabled: Flow<Boolean> = store.data
        .map { it[ENGINE_ENABLED] ?: true }
        .distinctUntilChanged()

    suspend fun setEngineEnabled(enabled: Boolean) {
        store.edit { it[ENGINE_ENABLED] = enabled }
    }

    private companion object {
        val ENGINE_ENABLED = booleanPreferencesKey("engine_enabled")
    }
}
