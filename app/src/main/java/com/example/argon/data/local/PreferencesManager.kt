package com.example.argon.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.argonPrefs by preferencesDataStore(name = "argon_cockpit_prefs")

data class CockpitPreferences(
    val nightMode: Boolean = false,
    val darkCockpit: Boolean = true,
    val voiceEnabled: Boolean = true,
    val simulationMode: Boolean = true,
    val offlineFirstMode: Boolean = false,
    val lastOriginCode: String = "CMX",
    val lastDestinationCode: String = "QUE"
)

class PreferencesManager(private val context: Context) {
    private object Keys {
        val NIGHT_MODE = booleanPreferencesKey("night_mode")
        val DARK_COCKPIT = booleanPreferencesKey("dark_cockpit")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val SIMULATION_MODE = booleanPreferencesKey("simulation_mode")
        val OFFLINE_FIRST = booleanPreferencesKey("offline_first")
        val ORIGIN_CODE = stringPreferencesKey("origin_code")
        val DEST_CODE = stringPreferencesKey("dest_code")
    }

    val preferencesFlow: Flow<CockpitPreferences> = context.argonPrefs.data.map { prefs ->
        CockpitPreferences(
            nightMode = prefs[Keys.NIGHT_MODE] ?: false,
            darkCockpit = prefs[Keys.DARK_COCKPIT] ?: true,
            voiceEnabled = prefs[Keys.VOICE_ENABLED] ?: true,
            simulationMode = prefs[Keys.SIMULATION_MODE] ?: true,
            offlineFirstMode = prefs[Keys.OFFLINE_FIRST] ?: false,
            lastOriginCode = prefs[Keys.ORIGIN_CODE] ?: "CMX",
            lastDestinationCode = prefs[Keys.DEST_CODE] ?: "QUE"
        )
    }

    suspend fun setNightMode(enabled: Boolean) {
        context.argonPrefs.edit { it[Keys.NIGHT_MODE] = enabled }
    }

    suspend fun setDarkCockpit(enabled: Boolean) {
        context.argonPrefs.edit { it[Keys.DARK_COCKPIT] = enabled }
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.argonPrefs.edit { it[Keys.VOICE_ENABLED] = enabled }
    }

    suspend fun setSimulationMode(enabled: Boolean) {
        context.argonPrefs.edit { it[Keys.SIMULATION_MODE] = enabled }
    }

    suspend fun setOfflineFirstMode(enabled: Boolean) {
        context.argonPrefs.edit { it[Keys.OFFLINE_FIRST] = enabled }
    }

    suspend fun setLastCorridor(originCode: String, destCode: String) {
        context.argonPrefs.edit {
            it[Keys.ORIGIN_CODE] = originCode
            it[Keys.DEST_CODE] = destCode
        }
    }
}
