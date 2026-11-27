package com.mue.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mue.core.database.SearchHistoryDao
import com.mue.core.database.SearchHistoryEntity
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.legacySearchHistoryDataStore by preferencesDataStore(name = "search_history")

@Serializable
private data class LegacySearchHistoryDto(
    val query: String,
    val type: String = "track",
    val artworkUrl: String? = null,
    val title: String? = null,
    val subtitle: String = "Song",
    val id: String? = null,
    val timestamp: Long? = null
)

data class SearchHistoryItem(
    val query: String,
    val type: String = "track",
    val artworkUrl: String? = null,
    val title: String = query,
    val subtitle: String = "Song",
    val id: String = query,
    val timestamp: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = timestamp,
    val track: Track? = null
)

@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchHistoryDao: SearchHistoryDao
) {
    private val legacyKey = stringPreferencesKey("recent_searches_json")
    private val migratedKey = booleanPreferencesKey("migrated_to_room")

    private suspend fun migrateLegacyIfNeeded() {
        val prefs = context.legacySearchHistoryDataStore.data.first()
        if (prefs[migratedKey] == true) return
        val jsonStr = prefs[legacyKey]
        if (!jsonStr.isNullOrBlank() && jsonStr != "[]") {
            val items = parseJson(jsonStr)
            items.forEach { item ->
                searchHistoryDao.upsert(
                    SearchHistoryEntity(
                        query = item.query,
                        title = item.title,
                        subtitle = item.subtitle,
                        type = item.type,
                        artworkUrl = item.artworkUrl,
                        id = item.id,
                        timestamp = item.timestamp
                    )
                )
            }
            searchHistoryDao.trimTo20()
        }
        context.legacySearchHistoryDataStore.edit { mutable ->
            mutable.remove(legacyKey)
            mutable[migratedKey] = true
        }
    }

    private fun normalizeKey(raw: String): String = raw.trim().lowercase()

    fun getRecentSearches(): Flow<List<SearchHistoryItem>> =
        searchHistoryDao.observeRecent()
            .onStart { runCatching { migrateLegacyIfNeeded() } }
            .map { entities ->
                entities.map { entity ->
                    SearchHistoryItem(
                        query = entity.query,
                        type = entity.type,
                        artworkUrl = entity.artworkUrl,
                        title = entity.title,
                        subtitle = entity.subtitle,
                        id = entity.id,
                        timestamp = entity.timestamp,
                        lastPlayedAt = entity.lastPlayedAt,
                        track = entity.trackJson?.let { decodeTrack(it) }
                    )
                }
            }

    suspend fun addSearch(
        query: String,
        type: String = "track",
        artworkUrl: String? = null,
        title: String? = null,
        subtitle: String? = null,
        id: String? = null
    ) {
        val trimmed = query.trim()
        val key = normalizeKey(trimmed)
        if (key.isEmpty()) return
        runCatching { migrateLegacyIfNeeded() }

        val now = System.currentTimeMillis()
        val targetId = id ?: trimmed
        val displayTitle = title ?: trimmed
        val displaySubtitle = subtitle ?: if (type.equals("artist", ignoreCase = true)) "Artist" else "Song"

        searchHistoryDao.upsert(
            SearchHistoryEntity(
                query = key,
                title = displayTitle,
                subtitle = displaySubtitle,
                type = type,
                artworkUrl = artworkUrl,
                id = targetId,
                timestamp = now,
                lastPlayedAt = now,
                trackJson = null
            )
        )
        searchHistoryDao.trimTo20()
    }

    suspend fun addTrackPlay(track: Track) {
        val key = normalizeKey(track.id)
        if (key.isEmpty()) return
        runCatching { migrateLegacyIfNeeded() }

        val now = System.currentTimeMillis()
        searchHistoryDao.upsert(
            SearchHistoryEntity(
                query = key,
                title = track.title,
                subtitle = track.artist,
                type = "track",
                artworkUrl = track.artworkUrl,
                id = track.id,
                timestamp = now,
                lastPlayedAt = now,
                trackJson = encodeTrack(track)
            )
        )
        searchHistoryDao.trimTo20()
    }

    suspend fun removeSearch(query: String) {
        searchHistoryDao.remove(normalizeKey(query))
    }

    suspend fun clearHistory() {
        searchHistoryDao.clear()
    }

    private val trackJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
    }

    internal fun encodeTrack(track: Track): String =
        trackJson.encodeToString(track)

    internal fun decodeTrack(raw: String): Track? =
        runCatching { trackJson.decodeFromString<Track>(raw) }.getOrNull()

    private fun parseJson(jsonStr: String): List<SearchHistoryItem> {
        return runCatching {
            trackJson.decodeFromString<List<LegacySearchHistoryDto>>(jsonStr).map { dto ->
                SearchHistoryItem(
                    query = dto.query,
                    type = dto.type,
                    artworkUrl = dto.artworkUrl?.ifEmpty { null },
                    title = dto.title ?: dto.query,
                    subtitle = dto.subtitle,
                    id = dto.id ?: dto.query,
                    timestamp = dto.timestamp ?: System.currentTimeMillis()
                )
            }
        }.getOrDefault(emptyList())
    }
}
