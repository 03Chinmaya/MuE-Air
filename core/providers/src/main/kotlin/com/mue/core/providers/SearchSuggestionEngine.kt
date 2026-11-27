package com.mue.core.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * SearchSuggestionEngine:
 * Combines YouTube Music's `music/get_search_suggestions` (gl: "IN") with local fuzzy matching
 * (Levenshtein + token similarity) so typos, regional keywords, moods, and partial queries
 * always return relatable recommendations.
 */
@Singleton
class SearchSuggestionEngine @Inject constructor(
    private val innerTubeXProvider: InnerTubeXProvider,
    private val jioSaavnProvider: JioSaavnProvider
) {
    suspend fun getSuggestions(
        query: String,
        localCandidates: List<String> = emptyList()
    ): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val remote = runCatching { innerTubeXProvider.getSearchSuggestions(trimmed) }
            .getOrDefault(emptyList())

        val saavnFallback = if (remote.size < 3) {
            runCatching { jioSaavnProvider.search(trimmed) }
                .getOrDefault(emptyList())
                .map { it.title }
                .filter { it.isNotBlank() }
                .take(5)
        } else emptyList()

        val fuzzyLocal = if (localCandidates.isNotEmpty()) {
            localCandidates
                .distinct()
                .map { it to similarity(trimmed.lowercase(), it.lowercase()) }
                .filter { it.second >= 0.45 }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(5)
        } else emptyList()

        (remote + saavnFallback + fuzzyLocal)
            .distinctBy { it.lowercase().trim() }
            .take(10)
    }

    fun bestFuzzyMatch(query: String, candidates: List<String>): String? {
        val q = query.trim().lowercase()
        if (q.isBlank() || candidates.isEmpty()) return null
        return candidates
            .map { it to similarity(q, it.lowercase()) }
            .filter { it.second >= 0.50 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (b.contains(a) || a.contains(b)) return 0.85
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        val dist = levenshtein(a, b)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(lhs: String, rhs: String): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var costs = IntArray(rhsLen + 1) { it }
        for (i in 1..lhsLen) {
            val newCosts = IntArray(rhsLen + 1)
            newCosts[0] = i
            for (j in 1..rhsLen) {
                val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                val costReplace = costs[j - 1] + match
                val costInsert = costs[j] + 1
                val costDelete = newCosts[j - 1] + 1
                newCosts[j] = min(min(costInsert, costDelete), costReplace)
            }
            costs = newCosts
        }
        return costs[rhsLen]
    }
}
