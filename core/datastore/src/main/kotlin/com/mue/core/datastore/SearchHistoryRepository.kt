package com.mue.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

data class SearchHistoryItem(
    val query: String,
    val type: String = "track",
    val artworkUrl: String? = null,
    val title: String = query,
    val subtitle: String = "Song",
    val id: String = query,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.searchHistoryDataStore
    private val key = stringPreferencesKey("recent_searches_json")

    fun getRecentSearches(): Flow<List<SearchHistoryItem>> = dataStore.data.map { prefs ->
        val jsonStr = prefs[key] ?: "[]"
        parseJson(jsonStr)
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
        if (trimmed.isEmpty()) return

        val current = getRecentSearches().first()
        val targetId = id ?: trimmed
        val displayTitle = title ?: trimmed
        val displaySubtitle = subtitle ?: if (type.equals("artist", ignoreCase = true)) "Artist" else "Song"

        val newItem = SearchHistoryItem(
            query = trimmed,
            type = type,
            artworkUrl = artworkUrl,
            title = displayTitle,
            subtitle = displaySubtitle,
            id = targetId,
            timestamp = System.currentTimeMillis()
        )

        val updated = (listOf(newItem) + current.filterNot {
            it.id.equals(targetId, ignoreCase = true) || it.query.equals(trimmed, ignoreCase = true)
        }).take(MAX_ENTRIES)

        save(updated)
    }

    suspend fun removeSearch(query: String) {
        val current = getRecentSearches().first()
        val updated = current.filterNot {
            it.query.equals(query, ignoreCase = true) || it.id.equals(query, ignoreCase = true)
        }
        save(updated)
    }

    suspend fun clearHistory() {
        save(emptyList())
    }

    private suspend fun save(items: List<SearchHistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("query", item.query)
                put("type", item.type)
                put("artworkUrl", item.artworkUrl ?: "")
                put("title", item.title)
                put("subtitle", item.subtitle)
                put("id", item.id)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        dataStore.edit { prefs ->
            prefs[key] = array.toString()
        }
    }

    private fun parseJson(jsonStr: String): List<SearchHistoryItem> {
        return try {
            val array = JSONArray(jsonStr)
            val result = mutableListOf<SearchHistoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawArtwork = obj.optString("artworkUrl", "")
                result.add(
                    SearchHistoryItem(
                        query = obj.getString("query"),
                        type = obj.optString("type", "track"),
                        artworkUrl = if (rawArtwork.isNotEmpty()) rawArtwork else null,
                        title = obj.optString("title", obj.getString("query")),
                        subtitle = obj.optString("subtitle", "Song"),
                        id = obj.optString("id", obj.getString("query")),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val MAX_ENTRIES = 20
    }
}

