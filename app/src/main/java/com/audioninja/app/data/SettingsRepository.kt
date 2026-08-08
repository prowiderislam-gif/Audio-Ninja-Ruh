package com.audioninja.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "audio_ninja_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val OUTPUT_FORMAT = stringPreferencesKey("output_format")
        val SAMPLE_RATE = intPreferencesKey("sample_rate")
        val BITRATE = intPreferencesKey("bitrate")
        val STEREO = booleanPreferencesKey("stereo")
        val RECORD_MIC_WITH_INTERNAL = booleanPreferencesKey("record_mic_with_internal")
        val SAVE_TO_EXTERNAL = booleanPreferencesKey("save_to_external")
        val AUTO_KEEP_BACKGROUND = booleanPreferencesKey("auto_keep_background")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    }

    val outputFormat: Flow<String> = context.dataStore.data.map { it[Keys.OUTPUT_FORMAT] ?: "AAC (M4A)" }
    val sampleRate: Flow<Int> = context.dataStore.data.map { it[Keys.SAMPLE_RATE] ?: 48000 }
    val bitrate: Flow<Int> = context.dataStore.data.map { it[Keys.BITRATE] ?: 320000 }
    val stereo: Flow<Boolean> = context.dataStore.data.map { it[Keys.STEREO] ?: true }
    val recordMicWithInternal: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.RECORD_MIC_WITH_INTERNAL] ?: false }
    val saveToExternal: Flow<Boolean> = context.dataStore.data.map { it[Keys.SAVE_TO_EXTERNAL] ?: false }
    val autoKeepBackground: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_KEEP_BACKGROUND] ?: true }
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.APP_LOCK_ENABLED] ?: false }

    suspend fun setOutputFormat(value: String) = context.dataStore.edit { it[Keys.OUTPUT_FORMAT] = value }
    suspend fun setSampleRate(value: Int) = context.dataStore.edit { it[Keys.SAMPLE_RATE] = value }
    suspend fun setBitrate(value: Int) = context.dataStore.edit { it[Keys.BITRATE] = value }
    suspend fun setStereo(value: Boolean) = context.dataStore.edit { it[Keys.STEREO] = value }
    suspend fun setRecordMicWithInternal(value: Boolean) =
        context.dataStore.edit { it[Keys.RECORD_MIC_WITH_INTERNAL] = value }
    suspend fun setSaveToExternal(value: Boolean) = context.dataStore.edit { it[Keys.SAVE_TO_EXTERNAL] = value }
    suspend fun setAutoKeepBackground(value: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_KEEP_BACKGROUND] = value }
    suspend fun setAppLockEnabled(value: Boolean) = context.dataStore.edit { it[Keys.APP_LOCK_ENABLED] = value }
}
