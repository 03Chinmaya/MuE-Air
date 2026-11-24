package com.mue.core.providerchain

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProviderChainExecutorTest {

    @Test
    fun `firstOf returns first non-null successful link`() = runTest {
        val links = listOf(
            ChainLink("link1", setOf(Cap.METADATA), 1000),
            ChainLink("link2", setOf(Cap.METADATA), 1000)
        )

        val result = ProviderChainExecutor.firstOf(links, Cap.METADATA) { link ->
            when (link.id) {
                "link1" -> "Result 1"
                else -> "Result 2"
            }
        }

        assertNotNull(result)
        assertEquals("link1", result?.linkId)
        assertEquals("Result 1", result?.data)
    }

    @Test
    fun `firstOf falls to next link when first times out`() = runTest {
        val links = listOf(
            ChainLink("slowLink", setOf(Cap.METADATA), 50),
            ChainLink("fastLink", setOf(Cap.METADATA), 500)
        )

        val result = ProviderChainExecutor.firstOf(links, Cap.METADATA) { link ->
            when (link.id) {
                "slowLink" -> {
                    delay(200)
                    "Slow Result"
                }
                "fastLink" -> "Fast Result"
                else -> null
            }
        }

        assertNotNull(result)
        assertEquals("fastLink", result?.linkId)
        assertEquals("Fast Result", result?.data)
    }

    @Test
    fun `firstOf falls to next link on exception`() = runTest {
        val links = listOf(
            ChainLink("faultyLink", setOf(Cap.METADATA), 500),
            ChainLink("backupLink", setOf(Cap.METADATA), 500)
        )

        val result = ProviderChainExecutor.firstOf(links, Cap.METADATA) { link ->
            when (link.id) {
                "faultyLink" -> throw RuntimeException("Network down")
                "backupLink" -> "Backup Result"
                else -> null
            }
        }

        assertNotNull(result)
        assertEquals("backupLink", result?.linkId)
        assertEquals("Backup Result", result?.data)
    }

    @Test
    fun `firstOf skips disabled links`() = runTest {
        val links = listOf(
            ChainLink("disabledLink", setOf(Cap.METADATA), 500, enabled = false),
            ChainLink("activeLink", setOf(Cap.METADATA), 500, enabled = true)
        )

        val result = ProviderChainExecutor.firstOf(links, Cap.METADATA) { link ->
            "Result from ${link.id}"
        }

        assertNotNull(result)
        assertEquals("activeLink", result?.linkId)
    }

    @Test
    fun `firstOf returns null when all fail or return null`() = runTest {
        val links = listOf(
            ChainLink("link1", setOf(Cap.METADATA), 100),
            ChainLink("link2", setOf(Cap.METADATA), 100)
        )

        val result = ProviderChainExecutor.firstOf(links, Cap.METADATA) { null }
        assertNull(result)
    }

    @Test
    fun `verify provider chains order`() {
        val metadataOrder = ProviderChains.metadata.map { it.id }
        assertEquals(
            listOf("innertubex", "innertune", "server-youtubei", "server-ytmusicapi", "jiosaavn", "jamendo"),
            metadataOrder
        )

        val playbackOrder = ProviderChains.playback.map { it.id }
        assertEquals(
            listOf("downloaded", "jamendo", "jiosaavn", "innertubex", "innertune", "server-youtubei", "server-ytmusicapi", "local"),
            playbackOrder
        )
    }
}
