package com.audioninja.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore by preferencesDataStore(name = "audio_ninja_favorites")

/**
 * Persists favorite recording IDs to disk so they survive navigating away and
 * back, or restarting the app — unlike the previous in-memory-only approach.
 */
class FavoritesRepository(private val context: Context) {

    private object Keys {
        val FAVORITE_IDS = stringSetPreferencesKey("favorite_ids")
    }

    val favoriteIds: Flow<Set<String>> =
        context.favoritesDataStore.data.map { it[Keys.FAVORITE_IDS] ?: emptySet() }

    suspend fun toggleFavorite(id: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_IDS] ?: emptySet()
            prefs[Keys.FAVORITE_IDS] = if (id in current) current - id else current + id
        }
    }

    suspend fun setFavorite(id: String, isFavorite: Boolean) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_IDS] ?: emptySet()
            prefs[Keys.FAVORITE_IDS] = if (isFavorite) current + id else current - id
        }
    }
}
