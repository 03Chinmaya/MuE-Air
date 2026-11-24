package com.mue.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SyncOperationDto(
    val id: String? = null,
    val type: String, // "create", "update", "delete"
    val entityType: String, // "like", "playlist", "playlist_track", "history", "preference", "playlist_reorder", "import_playlist", "import_playlist_track"
    val entityId: String,
    val payload: JsonElement? = null,
    val createdAt: String? = null
)

@Serializable
data class UploadLocalRequest(
    val clientBatchId: String,
    val operations: List<SyncOperationDto>
)

@Serializable
data class UploadLocalResponse(
    val success: Boolean = false,
    val status: String? = null,
    val processed: Int = 0,
    val acceptedCount: Int = 0,
    val failedCount: Int = 0,
    val serverTime: String? = null
)

@Serializable
data class SyncPullItem(
    val entity: String,
    val type: String = "create",
    val payload: JsonElement,
    val updatedAt: String? = null
)

@Serializable
data class SyncPullResponse(
    val items: List<SyncPullItem> = emptyList(),
    val nextCursor: String? = null
)

@Serializable
data class CloudSettingDto(
    val key: String,
    val value: JsonElement,
    val deviceId: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class CloudSettingsResponse(
    val settings: List<CloudSettingDto> = emptyList()
)

@Serializable
data class CloudSettingsPayload(
    val settings: List<CloudSettingDto>
)

