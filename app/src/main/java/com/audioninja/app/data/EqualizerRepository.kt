package com.audioninja.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.equalizerDataStore by preferencesDataStore(name = "audio_ninja_equalizer")

/**
 * EQ settings apply globally to all playback (recordings and playlists alike)
 * via Android's built-in Equalizer/BassBoost audio effects, attached to
 * whichever audio session is currently playing.
 */
class EqualizerRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("eq_enabled")
        val BASS_BOOST = intPreferencesKey("eq_bass_boost") // 0-1000
        val BAND_LEVELS = stringPreferencesKey("eq_band_levels") // comma-separated millibels
    }

    val enabled: Flow<Boolean> = context.equalizerDataStore.data.map { it[Keys.ENABLED] ?: false }
    val bassBoost: Flow<Int> = context.equalizerDataStore.data.map { it[Keys.BASS_BOOST] ?: 0 }
    val bandLevels: Flow<List<Int>> = context.equalizerDataStore.data.map { prefs ->
        val raw = prefs[Keys.BAND_LEVELS] ?: ""
        if (raw.isBlank()) emptyList() else raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    suspend fun setEnabled(value: Boolean) {
        context.equalizerDataStore.edit { it[Keys.ENABLED] = value }
    }

    suspend fun setBassBoost(value: Int) {
        context.equalizerDataStore.edit { it[Keys.BASS_BOOST] = value }
    }

    suspend fun setBandLevels(levels: List<Int>) {
        context.equalizerDataStore.edit { it[Keys.BAND_LEVELS] = levels.joinToString(",") }
    }
}
