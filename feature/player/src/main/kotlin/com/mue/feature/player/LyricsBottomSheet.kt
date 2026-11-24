package com.mue.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mue.core.design.LocalHazeState
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.liquidGlass
import com.mue.core.lyrics.LyricsRepository
import com.mue.core.lyrics.model.LineLyric
import com.mue.core.lyrics.model.WordLyric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsBottomSheet(
    trackId: String,
    title: String,
    artist: String,
    positionMs: Long,
    lyricsRepository: LyricsRepository,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lyricsFlow = remember(trackId) {
        lyricsRepository.getLyrics(trackId, title, artist)
    }
    val trackLyrics by lyricsFlow.collectAsState(initial = null)
    val listState = rememberLazyListState()

    // Determine currently active line
    val currentLineIndex = remember(trackLyrics, positionMs) {
        val lines = trackLyrics?.lines.orEmpty()
        lines.indexOfFirst { positionMs in it.lineStartMs..it.lineEndMs }
            .takeIf { it >= 0 }
            ?: lines.indexOfLast { positionMs >= it.lineStartMs }.coerceAtLeast(0)
    }

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && trackLyrics?.lines?.isNotEmpty() == true) {
            listState.animateScrollToItem(
                index = currentLineIndex.coerceAtLeast(0),
                scrollOffset = -150
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = PureWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .liquidGlass(
                    hazeState = LocalHazeState.current,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(
                        text = "Lyrics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                    Text(
                        text = "$title • $artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey,
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = PureWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val lines = trackLyrics?.lines.orEmpty()
            if (lines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No synced lyrics available", color = MutedGrey)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        val isLineActive = index == currentLineIndex
                        LyricLineView(
                            line = line,
                            isActive = isLineActive,
                            currentPositionMs = positionMs,
                            onClick = { onSeekTo(line.lineStartMs) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineView(
    line: LineLyric,
    isActive: Boolean,
    currentPositionMs: Long,
    onClick: () -> Unit
) {
    val lineAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.5f,
        animationSpec = tween(300),
        label = "LineAlpha"
    )

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        if (line.words.isNotEmpty()) {
            line.words.forEach { word ->
                WordLyricItem(
                    word = word,
                    isLineActive = isActive,
                    currentPositionMs = currentPositionMs,
                    lineAlpha = lineAlpha
                )
            }
        } else {
            Text(
                text = line.text,
                color = PureWhite.copy(alpha = lineAlpha),
                fontSize = if (isActive) 24.sp else 20.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WordLyricItem(
    word: WordLyric,
    isLineActive: Boolean,
    currentPositionMs: Long,
    lineAlpha: Float
) {
    val isWordActive = isLineActive && currentPositionMs in word.startMs..word.endMs
    val isPastWord = isLineActive && currentPositionMs > word.endMs

    val scale by animateFloatAsState(
        targetValue = if (isWordActive) 1.08f else 1f,
        animationSpec = tween(150),
        label = "WordScale"
    )

    val color by animateColorAsState(
        targetValue = when {
            isWordActive -> Color(0xFF64B5F6) // Bright accent highlight
            isPastWord -> PureWhite
            isLineActive -> PureWhite.copy(alpha = 0.5f)
            else -> PureWhite.copy(alpha = lineAlpha)
        },
        animationSpec = tween(150),
        label = "WordColor"
    )

    Text(
        text = "${word.text} ",
        color = color,
        fontSize = if (isLineActive) 24.sp else 20.sp,
        fontWeight = if (isWordActive || isPastWord) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.scale(scale)
    )
}

