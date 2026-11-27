package com.mue.core.auth

/**
 * Service interface for backend authentication endpoints.
 */
interface AuthService {
    suspend fun login(email: String, password: String): AuthResponse
    suspend fun register(email: String, password: String, username: String): AuthResponse
    suspend fun guestSetup(deviceId: String, deviceName: String): AuthResponse
    suspend fun refresh(refreshToken: String): AuthResponse
    suspend fun mergeGuest(guestId: String, clientBatchId: String? = null): MergeGuestResponse
    suspend fun logout()
    suspend fun deleteAccount(): Boolean
    suspend fun getLinkedDevices(): List<DeviceDto>
    suspend fun revokeDevice(deviceId: String): Boolean
}
