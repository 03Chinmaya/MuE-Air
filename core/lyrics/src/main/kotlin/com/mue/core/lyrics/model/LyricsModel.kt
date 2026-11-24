package com.mue.core.lyrics.model

data class WordLyric(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class LineLyric(
    val lineStartMs: Long,
    val lineEndMs: Long,
    val text: String,
    val words: List<WordLyric> = emptyList()
)

data class TrackLyrics(
    val trackId: String,
    val lines: List<LineLyric> = emptyList(),
    val isWordSynced: Boolean = false
)

