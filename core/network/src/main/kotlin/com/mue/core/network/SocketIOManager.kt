package com.mue.core.network

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketIOManager @Inject constructor() {
    private var socket: Socket? = null
    private val eventHandlers = mutableListOf<Pair<String, Emitter.Listener>>()

    fun connect(token: String, deviceId: String? = null) {
        if (socket?.connected() == true) return
        val authMap = mutableMapOf<String, String>("token" to token)
        if (!deviceId.isNullOrBlank()) {
            authMap["deviceId"] = deviceId
        }
        val opts = IO.Options.builder()
            .setAuth(authMap)
            .setReconnection(true)
            .setReconnectionAttempts(Int.MAX_VALUE)
            .setReconnectionDelay(1000)
            .setReconnectionDelayMax(5000)
            .build()
        val s = IO.socket(NetworkConfig.wsUrl, opts)
        socket = s
        synchronized(eventHandlers) {
            for ((event, handler) in eventHandlers) {
                s.on(event, handler)
            }
        }
        s.connect()
    }

    private fun addListener(event: String, handler: Emitter.Listener) {
        synchronized(eventHandlers) {
            eventHandlers.add(event to handler)
            socket?.on(event, handler)
        }
    }

    fun isConnected(): Boolean = socket?.connected() == true

    fun onConnected(listener: () -> Unit) {
        addListener(Socket.EVENT_CONNECT) { listener() }
    }

    fun onDisconnected(listener: (reason: String?) -> Unit) {
        addListener(Socket.EVENT_DISCONNECT) { args ->
            val reason = if (args.isNotEmpty()) args[0]?.toString() else null
            listener(reason)
        }
    }

    fun onConnectionError(listener: (error: String) -> Unit) {
        addListener(Socket.EVENT_CONNECT_ERROR) { args ->
            val err = if (args.isNotEmpty()) args[0]?.toString() ?: "Connect error" else "Connect error"
            listener(err)
        }
    }

    fun onAuthFailed(listener: (message: String) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val msg = when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        payload.optString("message", "Authentication failed")
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        (payload["message"] as? String) ?: "Authentication failed"
                    }
                    else -> arg.toString()
                }
                listener(msg)
            }
        }
        addListener("auth_fail", handler)
    }

    fun onError(listener: (message: String) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val msg = when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        payload.optString("message", arg.optString("error", "Socket error"))
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        (payload["message"] as? String) ?: (arg["error"] as? String) ?: "Socket error"
                    }
                    else -> arg.toString()
                }
                listener(msg)
            }
        }
        addListener("error", handler)
    }

    fun onHostDisconnected(listener: (sessionId: String, graceSeconds: Int) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val sessionId: String
                val graceSeconds: Int
                when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        sessionId = payload.optString("sessionId", payload.optString("joinCode", ""))
                        graceSeconds = payload.optInt("graceSeconds", 60)
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        sessionId = (payload["sessionId"] as? String) ?: (payload["joinCode"] as? String) ?: ""
                        graceSeconds = (payload["graceSeconds"] as? Number)?.toInt() ?: 60
                    }
                    else -> return@Listener
                }
                listener(sessionId, graceSeconds)
            }
        }
        addListener("listen_host_disconnected", handler)
    }

    fun onHostReconnected(listener: (sessionId: String) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val sessionId: String = when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        payload.optString("sessionId", payload.optString("joinCode", ""))
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        (payload["sessionId"] as? String) ?: (payload["joinCode"] as? String) ?: ""
                    }
                    else -> ""
                }
                if (sessionId.isNotEmpty()) {
                    listener(sessionId)
                }
            }
        }
        addListener("listen_host_reconnected", handler)
    }



    fun emitListenTogetherSync(roomId: String, trackId: String, positionMs: Long, isPlaying: Boolean) {
        val payload = mapOf(
            "sessionId" to roomId,
            "roomId" to roomId,
            "trackId" to trackId,
            "providerId" to "jiosaavn",
            "positionMs" to positionMs,
            "playing" to isPlaying,
            "isPlaying" to isPlaying
        )
        socket?.emit("listen_together_sync", payload)
        socket?.emit("listen_sync", payload)
    }

    fun emitListenTogetherJoin(roomId: String) {
        val payload = mapOf("sessionId" to roomId, "room" to "listen:$roomId")
        socket?.emit("listen_join_room", payload)
        socket?.emit("join_room", mapOf("room" to "listen:$roomId"))
    }

    fun emitListenTogetherLeave(roomId: String) {
        val payload = mapOf("sessionId" to roomId, "room" to "listen:$roomId")
        socket?.emit("listen_together_leave", payload)
        socket?.emit("listen_participant_left", payload)
    }

    fun emitListenTogetherEnd(roomId: String) {
        val payload = mapOf("sessionId" to roomId, "room" to "listen:$roomId")
        socket?.emit("listen_together_end", payload)
        socket?.emit("listen_ended", payload)
    }

    fun onListenTogetherSync(listener: (roomId: String, trackId: String, positionMs: Long, isPlaying: Boolean) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val roomId: String
                val trackId: String
                val pos: Long
                val playing: Boolean

                when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        roomId = payload.optString("sessionId", payload.optString("roomId", ""))
                        trackId = payload.optString("trackId", "")
                        pos = payload.optLong("positionMs", 0L)
                        playing = payload.optBoolean("playing", payload.optBoolean("isPlaying", false))
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        roomId = (payload["sessionId"] as? String) ?: (payload["roomId"] as? String) ?: ""
                        trackId = (payload["trackId"] as? String) ?: ""
                        pos = (payload["positionMs"] as? Number)?.toLong() ?: 0L
                        playing = (payload["playing"] as? Boolean) ?: (payload["isPlaying"] as? Boolean) ?: false
                    }
                    else -> return@Listener
                }
                listener(roomId, trackId, pos, playing)
            }
        }
        addListener("listen_together_sync", handler)
        addListener("listen_sync", handler)
    }

    fun onListenTogetherEnded(listener: (roomId: String, reason: String?) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val roomId: String
                val reason: String?
                when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        roomId = payload.optString("sessionId", payload.optString("roomId", ""))
                        reason = if (payload.has("reason")) payload.optString("reason") else null
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        roomId = (payload["sessionId"] as? String) ?: (payload["roomId"] as? String) ?: ""
                        reason = payload["reason"] as? String
                    }
                    else -> {
                        roomId = ""
                        reason = null
                    }
                }
                if (roomId.isNotEmpty()) {
                    listener(roomId, reason)
                }
            }
        }
        addListener("listen_together_ended", handler)
        addListener("listen_ended", handler)
    }

    fun onListenTogetherParticipantJoined(listener: (roomId: String, userId: String) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val roomId: String
                val userId: String
                when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        roomId = payload.optString("sessionId", payload.optString("joinCode", ""))
                        userId = payload.optString("userId", "")
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        roomId = (payload["sessionId"] as? String) ?: (payload["joinCode"] as? String) ?: ""
                        userId = (payload["userId"] as? String) ?: ""
                    }
                    else -> return@Listener
                }
                listener(roomId, userId)
            }
        }
        addListener("listen_participant_joined", handler)
        addListener("listen_together_participant_joined", handler)
    }

    fun onListenTogetherParticipantLeft(listener: (roomId: String, userId: String) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val roomId: String
                val userId: String
                when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        roomId = payload.optString("sessionId", payload.optString("joinCode", ""))
                        userId = payload.optString("userId", "")
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        roomId = (payload["sessionId"] as? String) ?: (payload["joinCode"] as? String) ?: ""
                        userId = (payload["userId"] as? String) ?: ""
                    }
                    else -> return@Listener
                }
                listener(roomId, userId)
            }
        }
        addListener("listen_participant_left", handler)
        addListener("listen_together_participant_left", handler)
    }

    fun emitSyncPing(t0: Long, roomId: String) {
        val payload = mapOf("t0" to t0, "roomId" to roomId, "sessionId" to roomId)
        socket?.emit("sync_ping", payload)
    }

    fun onSyncPing(listener: (t0: Long, senderId: String, roomId: String) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val t0: Long
                val senderId: String
                val roomId: String
                when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        t0 = payload.optLong("t0", 0L)
                        senderId = payload.optString("senderId", "")
                        roomId = payload.optString("roomId", payload.optString("sessionId", ""))
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        t0 = (payload["t0"] as? Number)?.toLong() ?: 0L
                        senderId = (payload["senderId"] as? String) ?: ""
                        roomId = (payload["roomId"] as? String) ?: (payload["sessionId"] as? String) ?: ""
                    }
                    else -> return@Listener
                }
                listener(t0, senderId, roomId)
            }
        }
        addListener("sync_ping", handler)
    }

    fun emitSyncPong(t0: Long, t1: Long, roomId: String) {
        val payload = mapOf("t0" to t0, "t1" to t1, "roomId" to roomId, "sessionId" to roomId)
        socket?.emit("sync_pong", payload)
    }

    fun onSyncPong(listener: (t0: Long, t1: Long, roomId: String) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val t0: Long
                val t1: Long
                val roomId: String
                when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        t0 = payload.optLong("t0", 0L)
                        t1 = payload.optLong("t1", 0L)
                        roomId = payload.optString("roomId", payload.optString("sessionId", ""))
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        t0 = (payload["t0"] as? Number)?.toLong() ?: 0L
                        t1 = (payload["t1"] as? Number)?.toLong() ?: 0L
                        roomId = (payload["roomId"] as? String) ?: (payload["sessionId"] as? String) ?: ""
                    }
                    else -> return@Listener
                }
                listener(t0, t1, roomId)
            }
        }
        addListener("sync_pong", handler)
    }

    fun emitQueueUpdate(roomId: String, queueJson: String) {
        val payload = mapOf("sessionId" to roomId, "roomId" to roomId, "queue" to queueJson)
        socket?.emit("queue_update", payload)
    }

    fun emitChatMessage(roomId: String, text: String) {
        val payload = mapOf(
            "sessionId" to roomId,
            "roomId" to roomId,
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        )
        socket?.emit("chat:message", payload)
    }

    fun emitReaction(roomId: String, emoji: String, trackId: String) {
        val payload = mapOf(
            "sessionId" to roomId,
            "roomId" to roomId,
            "emoji" to emoji,
            "trackId" to trackId
        )
        socket?.emit("chat:reaction", payload)
    }

    fun emitVoiceActive(roomId: String, isActive: Boolean) {
        val payload = mapOf(
            "sessionId" to roomId,
            "roomId" to roomId,
            "isActive" to isActive
        )
        socket?.emit("voice:active", payload)
    }

    fun onChatMessage(listener: (userId: String, text: String, timestamp: Long) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val userId: String
                val text: String
                val timestamp: Long
                when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        userId = payload.optString("userId", payload.optString("senderId", "Guest"))
                        text = payload.optString("text", "")
                        timestamp = payload.optLong("timestamp", System.currentTimeMillis())
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        userId = (payload["userId"] as? String) ?: (payload["senderId"] as? String) ?: "Guest"
                        text = (payload["text"] as? String) ?: ""
                        timestamp = (payload["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    }
                    else -> return@Listener
                }
                if (text.isNotBlank()) {
                    listener(userId, text, timestamp)
                }
            }
        }
        addListener("chat:message", handler)
    }

    fun onReaction(listener: (userId: String, emoji: String, trackId: String) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val userId: String
                val emoji: String
                val trackId: String
                when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        userId = payload.optString("userId", "Guest")
                        emoji = payload.optString("emoji", "")
                        trackId = payload.optString("trackId", "")
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        userId = (payload["userId"] as? String) ?: "Guest"
                        emoji = (payload["emoji"] as? String) ?: ""
                        trackId = (payload["trackId"] as? String) ?: ""
                    }
                    else -> return@Listener
                }
                if (emoji.isNotBlank()) {
                    listener(userId, emoji, trackId)
                }
            }
        }
        addListener("chat:reaction", handler)
    }

    fun onVoiceActive(listener: (userId: String, isActive: Boolean) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val userId: String
                val isActive: Boolean
                when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        userId = payload.optString("userId", "Guest")
                        isActive = payload.optBoolean("isActive", false)
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        userId = (payload["userId"] as? String) ?: "Guest"
                        isActive = (payload["isActive"] as? Boolean) ?: false
                    }
                    else -> return@Listener
                }
                listener(userId, isActive)
            }
        }
        addListener("voice:active", handler)
    }

    fun onQueueUpdate(listener: (queueJson: String) -> Unit) {
        val handler = Emitter.Listener { args ->
            if (args.isNotEmpty()) {
                val arg = args[0]
                val queueJson: String = when (arg) {
                    is JSONObject -> {
                        val payload = arg.optJSONObject("payload") ?: arg
                        payload.optString("queue", "")
                    }
                    is Map<*, *> -> {
                        val payload = (arg["payload"] as? Map<*, *>) ?: arg
                        (payload["queue"] as? String) ?: ""
                    }
                    else -> ""
                }
                if (queueJson.isNotEmpty()) {
                    listener(queueJson)
                }
            }
        }
        addListener("queue_update", handler)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
