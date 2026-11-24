package com.mue.core.lyrics

import com.mue.core.lyrics.model.LineLyric
import com.mue.core.lyrics.model.TrackLyrics
import com.mue.core.lyrics.model.WordLyric
import java.util.regex.Pattern

object EnhancedLrcParser {
    private val LINE_TIME_REGEX = Pattern.compile("^\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?](.*)")
    private val WORD_TIME_REGEX = Pattern.compile("<(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?>([^<]*)")

    fun parse(trackId: String, lrcContent: String): TrackLyrics {
        val lines = mutableListOf<LineLyric>()
        var hasWordTimestamps = false

        val rawLines = lrcContent.lines()
        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") || 
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:")) {
                continue
            }

            val lineMatcher = LINE_TIME_REGEX.matcher(trimmed)
            if (!lineMatcher.matches()) continue

            val min = lineMatcher.group(1)?.toLongOrNull() ?: 0L
            val sec = lineMatcher.group(2)?.toLongOrNull() ?: 0L
            val fracStr = lineMatcher.group(3) ?: "00"
            val ms = when (fracStr.length) {
                1 -> fracStr.toLong() * 100
                2 -> fracStr.toLong() * 10
                else -> fracStr.toLong()
            }
            val lineStartMs = (min * 60 + sec) * 1000 + ms
            val lineBody = lineMatcher.group(4) ?: ""

            val words = mutableListOf<WordLyric>()
            val wordMatcher = WORD_TIME_REGEX.matcher(lineBody)
            var lastWordEnd = lineStartMs
            val plainTextBuilder = StringBuilder()

            while (wordMatcher.find()) {
                hasWordTimestamps = true
                val wMin = wordMatcher.group(1)?.toLongOrNull() ?: 0L
                val wSec = wordMatcher.group(2)?.toLongOrNull() ?: 0L
                val wFracStr = wordMatcher.group(3) ?: "00"
                val wMs = when (wFracStr.length) {
                    1 -> wFracStr.toLong() * 100
                    2 -> wFracStr.toLong() * 10
                    else -> wFracStr.toLong()
                }
                val wordStartMs = (wMin * 60 + wSec) * 1000 + wMs
                val wordText = wordMatcher.group(4) ?: ""
                plainTextBuilder.append(wordText)

                // Adjust previous word endMs if needed
                if (words.isNotEmpty()) {
                    val prev = words.removeAt(words.size - 1)
                    words.add(prev.copy(endMs = wordStartMs))
                }

                val estimatedEnd = wordStartMs + 500L
                words.add(WordLyric(text = wordText, startMs = wordStartMs, endMs = estimatedEnd))
                lastWordEnd = estimatedEnd
            }

            val fullText = if (hasWordTimestamps && words.isNotEmpty()) {
                plainTextBuilder.toString().trim()
            } else {
                lineBody.trim()
            }

            if (words.isEmpty() && fullText.isNotEmpty()) {
                // If line does not have word-by-word tags, split by space with interpolated timings
                val tokens = fullText.split(" ")
                val approxDuration = 3000L
                val perToken = (approxDuration / tokens.size.coerceAtLeast(1)).coerceAtLeast(200L)
                tokens.forEachIndexed { idx, token ->
                    val wStart = lineStartMs + (idx * perToken)
                    val wEnd = wStart + perToken
                    words.add(WordLyric(text = "$token ", startMs = wStart, endMs = wEnd))
                }
            }

            lines.add(
                LineLyric(
                    lineStartMs = lineStartMs,
                    lineEndMs = lastWordEnd.coerceAtLeast(lineStartMs + 2000L),
                    text = fullText,
                    words = words
                )
            )
        }

        // Fallback for plain text lyrics without time tags
        if (lines.isEmpty()) {
            val plainLines = rawLines.map { it.trim() }.filter { it.isNotEmpty() }
            val perLineMs = 4000L
            plainLines.forEachIndexed { idx, text ->
                val startMs = idx * perLineMs
                val endMs = startMs + perLineMs
                val tokens = text.split(" ")
                val perToken = (perLineMs / tokens.size.coerceAtLeast(1)).coerceAtLeast(200L)
                val words = tokens.mapIndexed { wIdx, token ->
                    val wStart = startMs + (wIdx * perToken)
                    val wEnd = (wStart + perToken).coerceAtMost(endMs)
                    WordLyric(text = "$token ", startMs = wStart, endMs = wEnd)
                }
                lines.add(LineLyric(lineStartMs = startMs, lineEndMs = endMs, text = text, words = words))
            }
        }

        // Adjust lineEndMs to the next line's lineStartMs
        val adjustedLines = lines.mapIndexed { index, line ->
            val nextStart = if (index + 1 < lines.size) lines[index + 1].lineStartMs else line.lineStartMs + 4000L
            val adjustedWords = line.words.mapIndexed { wIdx, w ->
                val nextWStart = if (wIdx + 1 < line.words.size) line.words[wIdx + 1].startMs else nextStart
                w.copy(endMs = nextWStart)
            }
            line.copy(lineEndMs = nextStart, words = adjustedWords)
        }

        return TrackLyrics(
            trackId = trackId,
            lines = adjustedLines,
            isWordSynced = hasWordTimestamps
        )
    }
}
