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
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mue.core.design.DarkGrey
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.applePressEffect
import com.mue.core.design.liquidGlass
import com.mue.core.model.Track

@Composable
fun ArtistSongsScreen(
    onBack: () -> Unit = {},
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val currentPlayingId = playerState.currentTrack?.id
    val isPlaying = playerState.isPlaying

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        when (val state = uiState) {
            is ArtistUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp)
                }
            }

            is ArtistUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(cornerRadius = 24.dp)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Could not load songs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGrey
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .applePressEffect()
                                .clip(RoundedCornerShape(20.dp))
                                .background(PureWhite.copy(alpha = 0.15f))
                                .clickable { viewModel.loadArtist() }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Retry",
                                tint = PureWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PureWhite
                            )
                        }
                    }
                }
            }

            is ArtistUiState.Success -> {
                val artist = state.artist
                val topTracks = artist.topTracks.map { it.toTrack() }

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
                                text = artist.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${topTracks.size} songs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedGrey
                            )
                        }
                    }

                    // Full Songs List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(topTracks, key = { idx, t -> "${t.id}_$idx" }) { index, track ->
                            val isThisTrackPlaying = isPlaying && currentPlayingId == track.id

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .applePressEffect()
                                    .clickable { viewModel.playTrack(track) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Track Number or Playing EQ Icon
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (isThisTrackPlaying) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = "Playing",
                                            tint = Color(0xFF3B82F6),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MutedGrey,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Thumbnail Artwork (46dp, rounded 8dp, borderless)
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkGrey),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!track.artworkUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(track.artworkUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = track.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Track Title & Album
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isThisTrackPlaying) Color(0xFF3B82F6) else PureWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = if (!track.album.isNullOrBlank()) track.album else track.artist
                                    Text(
                                        text = sub ?: track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedGrey,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Duration
                                val minutes = track.durationMs / 60000
                                val seconds = (track.durationMs % 60000) / 1000
                                Text(
                                    text = String.format("%d:%02d", minutes, seconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedGrey
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
