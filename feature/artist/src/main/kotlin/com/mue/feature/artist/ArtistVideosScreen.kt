package com.mue.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.VideoListRow
import com.mue.core.design.applePressEffect
import com.mue.core.design.liquidGlass

@Composable
fun ArtistVideosScreen(
    onBack: () -> Unit = {},
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()

    val artistName = when (val state = uiState) {
        is ArtistUiState.Success -> state.artist.name
        else -> "Artist"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PureWhite
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$artistName - Videos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (videos.isNotEmpty()) "${videos.size} videos" else "Loading videos...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedGrey
                    )
                }
            }

            if (videos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp)
                }
            } else {
                // Vertical LazyColumn for Videos
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    itemsIndexed(
                        items = videos,
                        key = { _, video -> "vid_${video.videoId ?: video.id}" }
                    ) { index, video ->
                        VideoListRow(
                            title = video.title ?: video.name ?: "Video",
                            channelName = video.channelName ?: video.artist ?: artistName,
                            thumbnailUrl = video.thumbnail ?: video.artworkUrl,
                            durationMs = video.durationMs ?: (video.duration?.let { it * 1000 } ?: 0L),
                            onClick = { viewModel.playTrack(video.toTrack()) },
                            onMoreClick = null,
                            showDivider = index < videos.lastIndex
                        )
                    }
                }
            }
        }
    }
}
