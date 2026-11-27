package com.mue.core.auth

import android.content.Context
import com.mue.core.database.DatabaseWiper
import com.mue.core.datastore.SecureStore
import com.mue.core.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for authentication operations.
 */
interface AuthRepository {
    suspend fun login(email: String, password: String)
    suspend fun register(email: String, password: String, username: String)
    suspend fun getAccessToken(): String?
    suspend fun logout()
    suspend fun isAuthenticated(): Boolean
    suspend fun ensureGuestSession()
    suspend fun deleteAccount(): Boolean
    suspend fun restoreIdentity(mnemonic: String): Result<Unit>
    suspend fun getLinkedDevices(): List<DeviceDto>
    suspend fun revokeDevice(deviceId: String): Boolean
}

@Singleton
class DefaultAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authService: AuthService,
    private val tokenManager: TokenManager,
    private val secureStore: SecureStore,
    private val settingsDataStore: SettingsDataStore,
    private val databaseWiper: DatabaseWiper
) : AuthRepository {
    override suspend fun ensureGuestSession() {
        val currentAccess = secureStore.getAccessToken()
        val currentGuest = secureStore.getGuestToken()
        if (currentAccess.isNullOrBlank() && currentGuest.isNullOrBlank()) {
            val deviceId = settingsDataStore.getOrGenerateDeviceId()
            try {
                val res = authService.guestSetup(deviceId, android.os.Build.MODEL)
                secureStore.saveAccessToken(res.accessToken)
                secureStore.saveGuestToken(res.accessToken)
                tokenManager.saveAccessToken(res.accessToken)
                if (!res.refreshToken.isNullOrBlank()) {
                    secureStore.saveRefreshToken(res.refreshToken)
                }
                val uid = res.userId ?: res.user?.id
                if (uid != null) {
                    settingsDataStore.setUserId(uid)
                    settingsDataStore.setGuestId(uid)
                }
                settingsDataStore.setIsGuest(true)
            } catch (e: Exception) {
                android.util.Log.d("AuthRepository", "guest session offline: ${e.message}")
                settingsDataStore.setIsGuest(true)
            }
        }
    }

    override suspend fun login(email: String, password: String) {
        val res = authService.login(email, password)
        secureStore.saveAccessToken(res.accessToken)
        tokenManager.saveAccessToken(res.accessToken)
        if (!res.refreshToken.isNullOrBlank()) {
            secureStore.saveRefreshToken(res.refreshToken)
        }
        val uid = res.userId ?: res.user?.id
        if (uid != null) {
            settingsDataStore.setUserId(uid)
        }
        settingsDataStore.setIsGuest(false)
        if (res.merged == true) {
            settingsDataStore.setGuestId(null)
            com.mue.core.network.SyncWorker.enqueue(context)
        } else {
            val pendingGuest = settingsDataStore.guestIdFlow.first()
            if (!pendingGuest.isNullOrBlank()) {
                MergeWorker.enqueue(context)
            } else {
                com.mue.core.network.SyncWorker.enqueue(context)
            }
        }
    }

    override suspend fun register(email: String, password: String, username: String) {
        val res = authService.register(email, password, username)
        secureStore.saveAccessToken(res.accessToken)
        tokenManager.saveAccessToken(res.accessToken)
        if (!res.refreshToken.isNullOrBlank()) {
            secureStore.saveRefreshToken(res.refreshToken)
        }
        val uid = res.userId ?: res.user?.id
        if (uid != null) {
            settingsDataStore.setUserId(uid)
        }
        settingsDataStore.setIsGuest(false)
        if (res.merged == true) {
            settingsDataStore.setGuestId(null)
            com.mue.core.network.SyncWorker.enqueue(context)
        } else {
            val pendingGuest = settingsDataStore.guestIdFlow.first()
            if (!pendingGuest.isNullOrBlank()) {
                MergeWorker.enqueue(context)
            } else {
                com.mue.core.network.SyncWorker.enqueue(context)
            }
        }
    }

    override suspend fun getAccessToken(): String? = secureStore.getAccessToken() ?: tokenManager.getAccessToken()

    override suspend fun logout() {
        withTimeoutOrNull(3000L) {
            runCatching { authService.logout() }
        }
        databaseWiper.wipeAllTables()
        secureStore.clearAll()
        tokenManager.clear()
        settingsDataStore.clearAll()
        ensureGuestSession()
    }

    override suspend fun isAuthenticated(): Boolean {
        val token = getAccessToken()
        return !token.isNullOrBlank()
    }

    override suspend fun deleteAccount(): Boolean {
        return try {
            authService.deleteAccount()
            logout()
            true
        } catch (e: Exception) {
            logout()
            false
        }
    }

    override suspend fun restoreIdentity(mnemonic: String): Result<Unit> {
        val words = mnemonic.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size != 12 && words.size != 24) {
            return Result.failure(
                IllegalArgumentException("Mnemonic phrase must contain exactly 12 or 24 words (got ${words.size}).")
            )
        }
        val invalidWords = words.filter { !it.all { char -> char.isLetter() } }
        if (invalidWords.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException("Mnemonic phrase contains invalid characters.")
            )
        }
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(words.joinToString(" ").lowercase().toByteArray(Charsets.UTF_8))
            val hexHash = hashBytes.joinToString("") { "%02x".format(it) }
            val restoredUid = "usr_${hexHash.take(12)}"

            secureStore.saveAccessToken(hexHash)
            tokenManager.saveAccessToken(hexHash)
            settingsDataStore.setUserId(restoredUid)
            settingsDataStore.setIsGuest(false)
            com.mue.core.network.SyncWorker.enqueue(context)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLinkedDevices(): List<DeviceDto> {
        return authService.getLinkedDevices()
    }

    override suspend fun revokeDevice(deviceId: String): Boolean {
        return authService.revokeDevice(deviceId)
    }
}
