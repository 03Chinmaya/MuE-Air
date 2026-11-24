package com.mue.core.providerchain

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object ProviderChainExecutor {
    private const val TAG = "ProviderChain"

    private fun logDebug(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun logWarn(msg: String, error: Throwable? = null) {
        try {
            if (error != null) Log.w(TAG, msg, error) else Log.w(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] WARN: $msg ${error?.message ?: ""}")
        }
    }

    /**
     * Runs links in order, each inside withTimeout(budgetMs).
     * Returns the first non-null result as LinkResult<T>, or null if all fail.
     * Catches and logs failures per link, never throws.
     */
    suspend fun <T> firstOf(
        links: List<ChainLink>,
        capability: Cap,
        block: suspend (ChainLink) -> T?
    ): LinkResult<T>? {
        val eligibleLinks = links.filter { it.enabled && capability in it.caps }
        for (link in eligibleLinks) {
            val start = System.currentTimeMillis()
            try {
                val result = withTimeoutOrNull(link.budgetMs) {
                    block(link)
                }
                val duration = System.currentTimeMillis() - start
                if (result != null) {
                    logDebug("ChainLink '${link.id}' resolved ${capability.name} in ${duration}ms")
                    return LinkResult(linkId = link.id, data = result, durationMs = duration)
                } else {
                    logDebug("ChainLink '${link.id}' returned null or timed out after ${duration}ms")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val duration = System.currentTimeMillis() - start
                logWarn("ChainLink '${link.id}' failed with exception after ${duration}ms: ${e.message}", e)
            }
        }
        return null
    }

    /**
     * Hedged execution for search:
     * Starts the primary link immediately. If it has not returned within [hedgeAfterMs],
     * starts the remaining eligible links in parallel.
     * Returns the first non-null/non-empty result.
     */
    suspend fun <T> firstOfHedged(
        links: List<ChainLink>,
        capability: Cap,
        hedgeAfterMs: Long = 1200L,
        block: suspend (ChainLink) -> T?
    ): LinkResult<T>? = coroutineScope {
        val eligible = links.filter { it.enabled && capability in it.caps }
        if (eligible.isEmpty()) return@coroutineScope null
        val primary = eligible.first()
        val rest = eligible.drop(1)

        val channel = Channel<LinkResult<T>>(Channel.BUFFERED)

        val primaryJob = launch {
            val start = System.currentTimeMillis()
            try {
                val res = withTimeoutOrNull(primary.budgetMs) { block(primary) }
                val duration = System.currentTimeMillis() - start
                if (res != null) {
                    channel.trySend(LinkResult(primary.id, res, duration))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logWarn("Primary link '${primary.id}' failed: ${e.message}", e)
            }
        }

        val hedgeJob = launch {
            delay(hedgeAfterMs)
            for (link in rest) {
                launch {
                    val start = System.currentTimeMillis()
                    try {
                        val res = withTimeoutOrNull(link.budgetMs) { block(link) }
                        val duration = System.currentTimeMillis() - start
                        if (res != null) {
                            channel.trySend(LinkResult(link.id, res, duration))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logWarn("Hedged link '${link.id}' failed: ${e.message}", e)
                    }
                }
            }
        }

        val winner = channel.receiveCatching().getOrNull()
        primaryJob.cancel()
        hedgeJob.cancel()
        winner
    }
}
