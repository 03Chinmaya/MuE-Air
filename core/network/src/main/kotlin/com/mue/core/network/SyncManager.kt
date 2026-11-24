package com.mue.core.network

import android.util.Log
import com.mue.core.database.ImportedPlaylistEntity
import com.mue.core.database.ImportedPlaylistTrackDao
import com.mue.core.database.ImportedPlaylistTrackEntity
import com.mue.core.database.LikedSongEntity
import com.mue.core.database.PlaybackHistoryEntity
import com.mue.core.database.PlaylistEntity
import com.mue.core.database.PlaylistTrackEntity
import com.mue.core.database.SyncDao
import com.mue.core.datastore.SettingsDataStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncManager"

@Singleton
class SyncManager @Inject constructor(
    private val httpClient: HttpClient,
    private val syncDao: SyncDao,
    private val importedPlaylistTrackDao: ImportedPlaylistTrackDao,
    private val settingsDataStore: SettingsDataStore,
    private val settingsApiClient: SettingsApiClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun syncAll(): Boolean {
        return try {
            val uploadSuccess = uploadDirtyData()
            val pullSuccess = pullServerData()
            val settingsSuccess = syncSettings()
            uploadSuccess && pullSuccess && settingsSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception: ${e.message}", e)
            false
        }
    }

    /**
     * Upload phase: Reads all isDirty=1 entities from Room, creates batches <= 50,
     * posts to /api/sync/upload-local, and marks them synced.
     */
    suspend fun uploadDirtyData(): Boolean {
        val operations = mutableListOf<Pair<SyncOperationDto, suspend () -> Unit>>()

        // 1. Likes
        val dirtyLikes = syncDao.getDirtyLikes()
        for (like in dirtyLikes) {
            val payload = buildJsonObject {
                put("provider", like.provider)
                put("providerTrackId", like.providerTrackId)
                put("title", like.title)
                put("artistName", like.artist)
                like.album?.let { put("albumTitle", it) }
                like.artworkUrl?.let { put("artworkUrl", it) }
                put("durationMs", like.durationMs)
                put("likedAt", like.likedAt)
            }
            val op = SyncOperationDto(
                id = UUID.randomUUID().toString(),
                type = "create",
                entityType = "like",
                entityId = like.songId,
                payload = payload
            )
            operations.add(op to { syncDao.markLikedSongSynced(like.songId) })
        }

        // 2. Playlists
        val dirtyPlaylists = syncDao.getDirtyPlaylists()
        for (pl in dirtyPlaylists) {
            val payload = buildJsonObject {
                put("name", pl.name)
                pl.description?.let { put("description", it) }
                pl.artworkUrl?.let { put("artworkUrl", it) }
                put("visibility", pl.visibility)
                put("createdAt", pl.createdAt)
                put("updatedAt", pl.updatedAt)
            }
            val op = SyncOperationDto(
                id = UUID.randomUUID().toString(),
                type = "create",
                entityType = "playlist",
                entityId = pl.id,
                payload = payload
            )
            operations.add(op to { syncDao.markPlaylistSynced(pl.id) })
        }

        // 3. Playlist Tracks
        val dirtyPlaylistTracks = syncDao.getDirtyPlaylistTracks()
        for (pt in dirtyPlaylistTracks) {
            val payload = buildJsonObject {
                put("playlistId", pt.playlistId)
                put("position", pt.position)
                put("provider", pt.provider)
                put("providerTrackId", pt.providerTrackId)
                put("title", pt.title)
                put("artistName", pt.artist)
                pt.album?.let { put("albumTitle", it) }
                pt.artworkUrl?.let { put("artworkUrl", it) }
                put("durationMs", pt.durationMs)
                put("addedAt", pt.addedAt)
            }
            val op = SyncOperationDto(
                id = UUID.randomUUID().toString(),
                type = "create",
                entityType = "playlist_track",
                entityId = pt.id,
                payload = payload
            )
            operations.add(op to { syncDao.markPlaylistTrackSynced(pt.id) })
        }

        // 4. Play History
        val dirtyHistory = syncDao.getDirtyHistory()
        for (h in dirtyHistory) {
            val payload = buildJsonObject {
                put("provider", h.provider)
                put("providerTrackId", h.providerTrackId)
                put("title", h.title)
                put("artistName", h.artist)
                h.album?.let { put("albumTitle", it) }
                h.artworkUrl?.let { put("artworkUrl", it) }
                put("durationMs", h.durationMs)
                put("playedAt", h.playedAt)
                put("durationPlayedMs", h.durationPlayedMs)
                put("completed", h.completed)
            }
            val op = SyncOperationDto(
                id = UUID.randomUUID().toString(),
                type = "create",
                entityType = "history",
                entityId = h.id,
                payload = payload
            )
            operations.add(op to { syncDao.markHistorySynced(h.id) })
        }

        // 5. Imported Playlists
        val dirtyImported = syncDao.getDirtyImportedPlaylists()
        for (ip in dirtyImported) {
            val payload = buildJsonObject {
                put("provider", ip.source)
                put("externalId", ip.id)
                put("title", ip.name)
                ip.artworkUrl?.let { put("thumbnailUrl", it) }
                put("trackCount", ip.trackCount)
            }
            val op = SyncOperationDto(
                id = UUID.randomUUID().toString(),
                type = "create",
                entityType = "import_playlist",
                entityId = ip.id,
                payload = payload
            )
            operations.add(op to { syncDao.markImportedPlaylistSynced(ip.id) })
        }

        if (operations.isEmpty()) {
            return true
        }

        // Batch in slices of 50 operations
        var allSucceeded = true
        for (chunk in operations.chunked(50)) {
            val request = UploadLocalRequest(
                clientBatchId = UUID.randomUUID().toString(),
                operations = chunk.map { it.first }
            )

            val success = try {
                val response = NetworkConfig.executeWithFallback("/api/sync/upload-local") { url ->
                    httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }
                }
                if (response.status.isSuccess()) {
                    val resBody: UploadLocalResponse = response.body()
                    if (resBody.success) {
                        // Mark rows clean
                        for (item in chunk) {
                            item.second.invoke()
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chunk upload failed: ${e.message}", e)
                false
            }

            if (!success) {
                allSucceeded = false
            }
        }

        return allSucceeded
    }

    /**
     * Download phase: Queries /api/sync/pull with pagination cursor,
     * applies deltas to Room with Echo Prevention (skip if isDirty=1).
     */
    suspend fun pullServerData(): Boolean {
        var cursor = settingsDataStore.getSyncCursor()
        var hasMore = true
        var pullSucceeded = true

        while (hasMore) {
            val queryParams = buildString {
                if (!cursor.isNullOrBlank()) {
                    append("cursor=").append(cursor)
                }
            }
            val path = if (queryParams.isNotEmpty()) "/api/sync/pull?$queryParams" else "/api/sync/pull"

            val pullResponse: SyncPullResponse? = try {
                val response = NetworkConfig.executeWithFallback(path) { url ->
                    httpClient.get(url)
                }
                if (response.status.isSuccess()) {
                    response.body<SyncPullResponse>()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pull failed: ${e.message}", e)
                pullSucceeded = false
                null
            }

            if (pullResponse == null) {
                break
            }

            for (item in pullResponse.items) {
                try {
                    applyPullItem(item)
                } catch (e: Exception) {
                    Log.w(TAG, "Error applying item ${item.entity}: ${e.message}")
                }
            }

            if (!pullResponse.nextCursor.isNullOrBlank() && pullResponse.nextCursor != cursor) {
                cursor = pullResponse.nextCursor
                settingsDataStore.setSyncCursor(cursor)
            } else {
                hasMore = false
                // If items had an updatedAt, store the latest timestamp as cursor for incremental pull
                val latestUpdate = pullResponse.items.mapNotNull { it.updatedAt }.maxOrNull()
                if (latestUpdate != null) {
                    settingsDataStore.setSyncCursor(latestUpdate)
                }
            }
        }

        return pullSucceeded
    }

    private suspend fun applyPullItem(item: SyncPullItem) {
        val payload = item.payload as? JsonObject ?: return
        val entityType = item.entity
        val isDelete = item.type.equals("delete", ignoreCase = true)

        when (entityType) {
            "like" -> {
                val trackId = payload["providerTrackId"]?.jsonPrimitive?.content
                    ?: payload["trackId"]?.jsonPrimitive?.content
                    ?: payload["id"]?.jsonPrimitive?.content ?: return

                // Echo Prevention: Check if local isDirty
                if (syncDao.isLikedSongDirty(trackId) == true) {
                    return // Local changes win
                }

                if (isDelete) {
                    syncDao.deleteLikedSong(trackId)
                } else {
                    val entity = LikedSongEntity(
                        songId = trackId,
                        provider = payload["provider"]?.jsonPrimitive?.content ?: "youtube_music",
                        providerTrackId = trackId,
                        title = payload["title"]?.jsonPrimitive?.content ?: "Track",
                        artist = payload["artistName"]?.jsonPrimitive?.content
                            ?: payload["artist"]?.jsonPrimitive?.content ?: "Unknown Artist",
                        album = payload["albumTitle"]?.jsonPrimitive?.content
                            ?: payload["album"]?.jsonPrimitive?.content,
                        artworkUrl = payload["artworkUrl"]?.jsonPrimitive?.content,
                        durationMs = payload["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                        likedAt = parseDate(payload["likedAt"]?.jsonPrimitive?.content),
                        isDirty = false,
                        serverId = payload["id"]?.jsonPrimitive?.content
                    )
                    syncDao.upsertLikedSong(entity)
                }
            }

            "playlist" -> {
                val playlistId = payload["id"]?.jsonPrimitive?.content ?: return
                if (syncDao.isPlaylistDirty(playlistId) == true) {
                    return
                }

                if (isDelete) {
                    syncDao.deletePlaylist(playlistId)
                } else {
                    val entity = PlaylistEntity(
                        id = playlistId,
                        name = payload["name"]?.jsonPrimitive?.content ?: "Untitled Playlist",
                        description = payload["description"]?.jsonPrimitive?.content,
                        artworkUrl = payload["artworkUrl"]?.jsonPrimitive?.content,
                        visibility = payload["visibility"]?.jsonPrimitive?.content ?: "public",
                        createdAt = parseDate(payload["createdAt"]?.jsonPrimitive?.content),
                        updatedAt = parseDate(payload["updatedAt"]?.jsonPrimitive?.content),
                        isDirty = false,
                        serverId = playlistId
                    )
                    syncDao.upsertPlaylist(entity)
                }
            }

            "playlist_track" -> {
                val id = payload["id"]?.jsonPrimitive?.content ?: return
                val playlistId = payload["playlistId"]?.jsonPrimitive?.content ?: return
                if (syncDao.isPlaylistTrackDirty(id) == true) {
                    return
                }

                if (isDelete) {
                    syncDao.deletePlaylistTrack(playlistId, id)
                } else {
                    val trackId = payload["providerTrackId"]?.jsonPrimitive?.content ?: id
                    val entity = PlaylistTrackEntity(
                        id = id,
                        playlistId = playlistId,
                        position = payload["position"]?.jsonPrimitive?.intOrNull ?: 0,
                        provider = payload["provider"]?.jsonPrimitive?.content ?: "youtube_music",
                        providerTrackId = trackId,
                        title = payload["title"]?.jsonPrimitive?.content ?: "Track",
                        artist = payload["artistName"]?.jsonPrimitive?.content ?: "Unknown Artist",
                        album = payload["albumTitle"]?.jsonPrimitive?.content,
                        artworkUrl = payload["artworkUrl"]?.jsonPrimitive?.content,
                        durationMs = payload["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                        addedAt = parseDate(payload["addedAt"]?.jsonPrimitive?.content),
                        isDirty = false,
                        serverId = id
                    )
                    syncDao.upsertPlaylistTrack(entity)
                }
            }

            "history" -> {
                val id = payload["id"]?.jsonPrimitive?.content ?: return
                if (syncDao.isHistoryDirty(id) == true) {
                    return
                }

                if (isDelete) {
                    syncDao.deleteHistory(id)
                } else {
                    val trackId = payload["providerTrackId"]?.jsonPrimitive?.content ?: id
                    val entity = PlaybackHistoryEntity(
                        id = id,
                        provider = payload["provider"]?.jsonPrimitive?.content ?: "youtube_music",
                        providerTrackId = trackId,
                        title = payload["title"]?.jsonPrimitive?.content ?: "Track",
                        artist = payload["artistName"]?.jsonPrimitive?.content ?: "Unknown Artist",
                        album = payload["albumTitle"]?.jsonPrimitive?.content,
                        artworkUrl = payload["artworkUrl"]?.jsonPrimitive?.content,
                        durationMs = payload["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                        playedAt = parseDate(payload["playedAt"]?.jsonPrimitive?.content),
                        durationPlayedMs = payload["durationPlayedMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                        completed = payload["completed"]?.jsonPrimitive?.booleanOrNull ?: false,
                        isDirty = false,
                        serverId = id
                    )
                    syncDao.upsertHistory(entity)
                }
            }

            "import_playlist", "imported_playlist" -> {
                val id = payload["id"]?.jsonPrimitive?.content
                    ?: payload["externalId"]?.jsonPrimitive?.content ?: return

                if (syncDao.isImportedPlaylistDirty(id) == true) {
                    return
                }

                if (isDelete) {
                    syncDao.deleteImportedPlaylist(id)
                } else {
                    val entity = ImportedPlaylistEntity(
                        id = id,
                        name = payload["title"]?.jsonPrimitive?.content
                            ?: payload["name"]?.jsonPrimitive?.content ?: "Imported Playlist",
                        artworkUrl = payload["thumbnailUrl"]?.jsonPrimitive?.content
                            ?: payload["artworkUrl"]?.jsonPrimitive?.content,
                        trackCount = payload["trackCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        source = payload["provider"]?.jsonPrimitive?.content ?: "youtube_music",
                        importedAt = parseDate(payload["createdAt"]?.jsonPrimitive?.content),
                        isDirty = false,
                        serverId = id
                    )
                    syncDao.upsertImportedPlaylist(entity)
                }
            }

            "import_playlist_track", "imported_playlist_track" -> {
                val id = payload["id"]?.jsonPrimitive?.content ?: return
                val playlistId = payload["importedPlaylistId"]?.jsonPrimitive?.content
                    ?: payload["playlistId"]?.jsonPrimitive?.content ?: return
                if (isDelete) {
                    // Deleted track
                } else {
                    val entity = ImportedPlaylistTrackEntity(
                        id = id,
                        importedPlaylistId = playlistId,
                        position = payload["position"]?.jsonPrimitive?.intOrNull ?: 0,
                        provider = payload["provider"]?.jsonPrimitive?.content ?: "youtube",
                        providerTrackId = payload["providerTrackId"]?.jsonPrimitive?.content
                            ?: payload["externalVideoId"]?.jsonPrimitive?.content ?: id,
                        title = payload["title"]?.jsonPrimitive?.content ?: "Track",
                        artists = payload["artists"]?.jsonPrimitive?.content
                            ?: payload["artistName"]?.jsonPrimitive?.content ?: "Unknown Artist",
                        durationMs = payload["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                        artworkUrl = payload["artworkUrl"]?.jsonPrimitive?.content
                            ?: payload["thumbnailUrl"]?.jsonPrimitive?.content,
                        jiosaavnTrackId = payload["jiosaavnTrackId"]?.jsonPrimitive?.content
                    )
                    importedPlaylistTrackDao.insertAll(listOf(entity))
                }
            }
        }
    }

    private fun parseDate(str: String?): Long {
        if (str.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            java.time.Instant.parse(str).toEpochMilli()
        } catch (_: Exception) {
            str.toLongOrNull() ?: System.currentTimeMillis()
        }
    }

    /**
     * Settings Sync: Push non-device-specific local preferences and pull remote cloud settings.
     * Conflict resolution: Last-Write-Wins (handled on server side via updated_at).
     */
    suspend fun syncSettings(): Boolean {
        val syncEnabled = settingsDataStore.settingsSyncEnabledFlow.first()
        if (!syncEnabled) {
            Log.d(TAG, "Settings sync is disabled by user.")
            return true
        }

        val isGuest = settingsDataStore.isGuestFlow.first()
        if (isGuest) {
            Log.d(TAG, "Guest user - skipping cloud settings sync.")
            return true
        }

        return try {
            // 1. Collect local synced settings to push (excluding device-specific keys)
            val prefs = settingsDataStore.getPreferences()
            val localItems = mutableListOf<CloudSettingDto>()

            prefs[SettingsDataStore.HIGH_PERFORMANCE]?.let {
                localItems.add(CloudSettingDto("high_performance", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.LOSSLESS_AUDIO]?.let {
                localItems.add(CloudSettingDto("lossless_audio", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.SYNCED_LYRICS]?.let {
                localItems.add(CloudSettingDto("synced_lyrics", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.DOWNLOAD_WIFI_ONLY]?.let {
                localItems.add(CloudSettingDto("download_wifi_only", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.PREFER_MUSIC_ONLY]?.let {
                localItems.add(CloudSettingDto("prefer_music_only", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.LOUDNESS_NORM]?.let {
                localItems.add(CloudSettingDto("loudness_norm", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.AUTOMIX]?.let {
                localItems.add(CloudSettingDto("automix", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.SPATIAL_AUDIO]?.let {
                localItems.add(CloudSettingDto("spatial_audio", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.REDUCE_ANIMATION]?.let {
                localItems.add(CloudSettingDto("reduce_animation", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.REDUCE_DYNAMIC_BLUR]?.let {
                localItems.add(CloudSettingDto("reduce_dynamic_blur", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.LIQUID_GLASS]?.let {
                localItems.add(CloudSettingDto("liquid_glass", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.ALLOW_GUESTS_CONTROL]?.let {
                localItems.add(CloudSettingDto("allow_guests_control", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.LISTEN_SYNC_QUALITY_HIGH]?.let {
                localItems.add(CloudSettingDto("listen_sync_quality_high", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.DISAPPEARING_TTL_MS]?.let {
                localItems.add(CloudSettingDto("disappearing_ttl_ms", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.MEDIA_TTL_24H]?.let {
                localItems.add(CloudSettingDto("media_ttl_24h", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.READ_RECEIPTS_ENABLED]?.let {
                localItems.add(CloudSettingDto("read_receipts_enabled", JsonPrimitive(it)))
            }
            prefs[SettingsDataStore.THEME]?.let {
                localItems.add(CloudSettingDto("theme", JsonPrimitive(it)))
            }

            // 2. Push & receive latest cloud settings (POST returns latest state)
            val updatedSettings = if (localItems.isNotEmpty()) {
                val postResult = settingsApiClient.postSettings(localItems)
                if (postResult.isSuccess) {
                    postResult.getOrThrow()
                } else {
                    settingsApiClient.getSettings().getOrNull() ?: emptyList()
                }
            } else {
                settingsApiClient.getSettings().getOrNull() ?: emptyList()
            }

            // 3. Apply cloud settings to local DataStore
            applyCloudSettings(updatedSettings)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Settings sync failed: ${e.message}", e)
            false
        }
    }

    private suspend fun applyCloudSettings(settings: List<CloudSettingDto>) {
        if (settings.isEmpty()) return
        settingsDataStore.edit { prefs ->
            for (setting in settings) {
                try {
                    when (setting.key) {
                        "high_performance" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.HIGH_PERFORMANCE] = it }
                        "lossless_audio" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.LOSSLESS_AUDIO] = it }
                        "synced_lyrics" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.SYNCED_LYRICS] = it }
                        "download_wifi_only" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.DOWNLOAD_WIFI_ONLY] = it }
                        "prefer_music_only" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.PREFER_MUSIC_ONLY] = it }
                        "loudness_norm" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.LOUDNESS_NORM] = it }
                        "automix" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.AUTOMIX] = it }
                        "spatial_audio" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.SPATIAL_AUDIO] = it }
                        "reduce_animation" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.REDUCE_ANIMATION] = it }
                        "reduce_dynamic_blur" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.REDUCE_DYNAMIC_BLUR] = it }
                        "liquid_glass" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.LIQUID_GLASS] = it }
                        "allow_guests_control" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.ALLOW_GUESTS_CONTROL] = it }
                        "listen_sync_quality_high" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.LISTEN_SYNC_QUALITY_HIGH] = it }
                        "disappearing_ttl_ms" -> setting.value.jsonPrimitive.longOrNull?.let { prefs[SettingsDataStore.DISAPPEARING_TTL_MS] = it }
                        "media_ttl_24h" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.MEDIA_TTL_24H] = it }
                        "read_receipts_enabled" -> setting.value.jsonPrimitive.booleanOrNull?.let { prefs[SettingsDataStore.READ_RECEIPTS_ENABLED] = it }
                        "theme" -> setting.value.jsonPrimitive.contentOrNull?.let { prefs[SettingsDataStore.THEME] = it }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to apply setting ${setting.key}: ${e.message}")
                }
            }
        }
    }
}
