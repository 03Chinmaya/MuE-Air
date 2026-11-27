package com.mue.core.auth

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mue.core.datastore.SettingsDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class MergeStatus { IDLE, IN_PROGRESS, SUCCEEDED, FAILED }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MergeWorkerEntryPoint {
    fun authService(): AuthService
    fun settingsDataStore(): SettingsDataStore
}

class MergeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            MergeWorkerEntryPoint::class.java
        )
        val authService = entryPoint.authService()
        val settingsDataStore = entryPoint.settingsDataStore()

        val guestId = settingsDataStore.guestIdFlow.first()
        val userId = settingsDataStore.userIdFlow.first()

        // If guestId is null, nothing to merge
        if (guestId.isNullOrBlank()) {
            return Result.success()
        }

        // If user is still guest or not logged in as auth user yet, do not merge
        if (guestId == userId || userId.isNullOrBlank()) {
            return Result.success()
        }

        return try {
            val batchId = UUID.randomUUID().toString()
            val res = authService.mergeGuest(guestId = guestId, clientBatchId = batchId)
            if (res.status == "merged" || res.status == "already_merged") {
                settingsDataStore.setGuestId(null)
                com.mue.core.network.SyncWorker.enqueue(applicationContext)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "mue_merge_guest_worker"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<MergeWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun observeStatus(context: Context): Flow<MergeStatus> =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(WORK_NAME)
                .map { infos ->
                    when (infos.firstOrNull()?.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.RUNNING,
                        WorkInfo.State.BLOCKED -> MergeStatus.IN_PROGRESS
                        WorkInfo.State.FAILED -> MergeStatus.FAILED
                        WorkInfo.State.SUCCEEDED -> MergeStatus.SUCCEEDED
                        WorkInfo.State.CANCELLED,
                        null -> MergeStatus.IDLE
                    }
                }
    }
}
