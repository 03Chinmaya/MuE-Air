package com.mue.core.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseWiper @Inject constructor(
    private val database: MuEDatabase
) {
    suspend fun wipeAllTables() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }
}

