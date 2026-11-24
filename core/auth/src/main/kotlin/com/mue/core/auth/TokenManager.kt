package com.mue.core.auth

import com.mue.core.datastore.SecureStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val secureStore: SecureStore
) {
    fun getAccessToken(): String? = secureStore.getAccessToken()
    fun saveAccessToken(token: String) = secureStore.saveAccessToken(token)
    fun clear() {
        secureStore.clearAccessToken()
    }
}

