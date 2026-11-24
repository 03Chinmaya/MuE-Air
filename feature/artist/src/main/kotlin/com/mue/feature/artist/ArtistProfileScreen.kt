package com.mue.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mue.core.design.DarkGrey
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.VideoCarouselCard
import com.mue.core.design.applePressEffect
import com.mue.core.design.liquidGlass
import com.mue.core.model.Track

@Composable
fun ArtistProfileScreen(
    onBack: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToAllSongs: () -> Unit = {},
    onNavigateToAllVideos: () -> Unit = {},
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()

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
                            text = "Could not load artist",
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
                val currentPlayingId = playerState.currentTrack?.id
                val isPlaying = playerState.isPlaying

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Hero Section with Backdrop
                    item(key = "artist_hero") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                        ) {
                            // Blurred Backdrop Image
                            val banner = artist.bannerUrl ?: artist.artworkUrl
                            if (!banner.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(banner)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(20.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            // Dark Gradient Overlay for OLED Contrast
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.4f),
                                                PureBlack.copy(alpha = 0.75f),
                                                PureBlack
                                            )
                                        )
                                    )
                            )

                            // Foreground Content: Avatar, Name, Followers
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.Bottom,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Circular Avatar (96dp, Zero Border)
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(DarkGrey),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!artist.artworkUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(artist.artworkUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = artist.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = artist.name,
                                            tint = PureWhite.copy(alpha = 0.6f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Artist Name
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Subscribers / Monthly audience
                                val subText = artist.subscriberCount
                                    ?: (if (state.isFollowing) "Following" else "Artist")
                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedGrey
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Follow / Following + Play Actions Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Follow / Following Pill
                                    Box(
                                        modifier = Modifier
                                            .applePressEffect()
                                            .clip(RoundedCornerShape(24.dp))
                                            .then(
                                                if (state.isFollowing) {
                                                    Modifier.liquidGlass(cornerRadius = 24.dp)
                                                } else {
                                                    Modifier.background(Color(0xFF3B82F6))
                                                }
                                            )
                                            .clickable { viewModel.toggleFollow() }
                                            .padding(horizontal = 24.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (state.isFollowing) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = PureWhite,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Following",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PureWhite
                                                )
                                            } else {
                                                Text(
                                                    text = "Follow",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PureWhite
                                                )
                                            }
                                        }
                                    }

                                    // Play All Pill
                                    if (topTracks.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .applePressEffect()
                                                .clip(CircleShape)
                                                .background(PureWhite)
                                                .clickable { viewModel.playAll() }
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "Play All",
                                                tint = PureBlack,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        // Shuffle Button
                                        Box(
                                            modifier = Modifier
                                                .applePressEffect()
                                                .clip(CircleShape)
                                                .liquidGlass(cornerRadius = 20.dp)
                                                .clickable { viewModel.shuffle() }
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Shuffle,
                                                contentDescription = "Shuffle",
                                                tint = PureWhite,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Optimistic loading indicator for songs & albums
                    if (topTracks.isEmpty() && state.isContentLoading) {
                        item(key = "loading_tracks_indicator") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp)
                            }
                        }
                    }

                    // Popular Songs Header
                    if (topTracks.isNotEmpty()) {
                        item(key = "top_tracks_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Popular",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                                Row(
                                    modifier = Modifier
                                        .applePressEffect()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onNavigateToAllSongs() }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "See All",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MutedGrey,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "See All Songs",
                                        tint = MutedGrey,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // 5x10 Horizontal Song Grid (take up to 50 songs in columns of 5)
                        item(key = "top_tracks_grid") {
                            val columns = topTracks.chunked(5).take(10)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(columns, key = { colIdx, _ -> "col_$colIdx" }) { colIndex, columnTracks ->
                                    Column(
                                        modifier = Modifier.width(320.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        columnTracks.forEachIndexed { rowIndex, track ->
                                            val trackIndex = colIndex * 5 + rowIndex + 1
                                            val isThisTrackPlaying = isPlaying && currentPlayingId == track.id

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .applePressEffect()
                                                    .clickable { viewModel.playTrack(track) }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Track Number or Playing EQ Icon
                                                Box(
                                                    modifier = Modifier.width(26.dp),
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
                                                            text = "$trackIndex",
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

                                                Spacer(modifier = Modifier.width(12.dp))

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

                    // Videos Section
                    if (videos.isNotEmpty()) {
                        item(key = "videos_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Videos",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                                Row(
                                    modifier = Modifier
                                        .applePressEffect()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onNavigateToAllVideos() }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "See All",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MutedGrey,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "See All Videos",
                                        tint = MutedGrey,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        item(key = "videos_carousel") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(videos.take(10), key = { "vid_${it.videoId ?: it.id}" }) { video ->
                                    VideoCarouselCard(
                                        title = video.title ?: video.name ?: "Video",
                                        channelName = video.channelName ?: video.artist ?: artist.name,
                                        thumbnailUrl = video.thumbnail ?: video.artworkUrl,
                                        durationMs = video.durationMs ?: (video.duration?.let { it * 1000 } ?: 0L),
                                        onClick = { viewModel.playTrack(video.toTrack()) },
                                        width = 160.dp
                                    )
                                }
                            }
                        }
                    }

                    // Discography / Albums Section
                    if (artist.albums.isNotEmpty()) {
                        item(key = "albums_header") {
                            Text(
                                text = "Albums & Singles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
                            )
                        }

                        item(key = "albums_carousel") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(artist.albums, key = { it.id }) { album ->
                                    Column(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .applePressEffect()
                                            .clickable { onNavigateToAlbum(album.id) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(DarkGrey),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!album.artworkUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(album.artworkUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = album.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = album.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PureWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        val albumYear = album.year
                                        if (!albumYear.isNullOrBlank()) {
                                            Text(
                                                text = albumYear,
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
        }

        // Top Pinned Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
        }
    }
}

