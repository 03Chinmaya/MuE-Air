package com.mue.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mue.core.design.DarkGrey
import com.mue.core.design.LayoutTokens
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SfIcons
import com.mue.core.design.SystemAccentBlue
import com.mue.core.design.applePressEffect
import com.mue.core.design.liquidGlass
import com.mue.core.model.Track

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToArtist: (String) -> Unit = {},
    onTrackClick: ((Track, List<Track>, String) -> Unit)? = null,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        when (val state = uiState) {
            is CollectionUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TopDetailNav(onBack = onBack, title = state.type.uppercase())

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = PureWhite,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            if (state.initialTitle.isNotBlank()) {
                                Text(
                                    text = "Loading ${state.initialTitle}...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedGrey
                                )
                            }
                        }
                    }
                }
            }

            is CollectionUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TopDetailNav(onBack = onBack, title = "Error")

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
                                text = "Could not load collection",
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
                                    .clickable { viewModel.loadCollection() }
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
            }

            is CollectionUiState.Success -> {
                val collection = state.collection
                val tracks = collection.tracks
                val isPlaying = state.isPlaying
                val currentPlayingId = state.currentPlayingId

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LayoutTokens.ListBottomPaddingWithMiniPlayer)
                ) {
                    // Pinned Header / Top Bar
                    item(key = "header_nav") {
                        TopDetailNav(
                            onBack = onBack,
                            title = collection.type.uppercase()
                        )
                    }

                    // Hero Showcase
                    item(key = "collection_showcase") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Artwork
                            Box(
                                modifier = Modifier
                                    .size(190.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DarkGrey),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!collection.artworkUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = collection.artworkUrl,
                                        contentDescription = collection.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = PureWhite.copy(alpha = 0.4f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Type Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PureWhite.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = when {
                                        collection.isImported -> "IMPORTED PLAYLIST"
                                        collection.type.equals("album", true) -> "ALBUM"
                                        collection.type.equals("ep", true) -> "EP"
                                        else -> "PLAYLIST"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SystemAccentBlue,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Title
                            Text(
                                text = collection.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            if (collection.subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = collection.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedGrey,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .applePressEffect()
                                        .clickable {
                                            if (collection.type.equals("album", true) || collection.type.equals("ep", true)) {
                                                onNavigateToArtist(collection.subtitle)
                                            }
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${tracks.size} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGrey.copy(alpha = 0.8f)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Play and Shuffle Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Play All
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(PureWhite)
                                        .applePressEffect()
                                        .clickable {
                                            if (tracks.isNotEmpty()) {
                                                if (onTrackClick != null) {
                                                    onTrackClick(tracks.first(), tracks, collection.title)
                                                } else {
                                                    viewModel.playAll(shuffle = false)
                                                }
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play",
                                        tint = PureBlack,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Play",
                                        color = PureBlack,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Shuffle
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(PureWhite.copy(alpha = 0.12f))
                                        .applePressEffect()
                                        .clickable {
                                            if (tracks.isNotEmpty()) {
                                                if (onTrackClick != null) {
                                                    val shuffled = tracks.shuffled()
                                                    onTrackClick(shuffled.first(), shuffled, collection.title)
                                                } else {
                                                    viewModel.playAll(shuffle = true)
                                                }
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = PureWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Shuffle",
                                        color = PureWhite,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Tracks Header
                    item(key = "tracks_header") {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }

                    // Track rows
                    itemsIndexed(tracks, key = { index, item -> "${item.id}_$index" }) { index, track ->
                        val isCurrentTrack = isPlaying && currentPlayingId == track.id
                        CollectionTrackRow(
                            index = index + 1,
                            track = track,
                            isPlaying = isCurrentTrack,
                            onClick = {
                                if (onTrackClick != null) {
                                    onTrackClick(track, tracks, collection.title)
                                } else {
                                    viewModel.playTrack(track)
                                }
                            },
                            onMoreClick = {
                                viewModel.addToQueue(track)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopDetailNav(
    onBack: () -> Unit,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Icon(
                imageVector = SfIcons.ArrowBack,
                contentDescription = "Back",
                tint = PureWhite,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CollectionTrackRow(
    index: Int,
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .applePressEffect()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number or playing equalizer
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Playing",
                    tint = SystemAccentBlue,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGrey,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Thumbnail (optional, small 38.dp)
        if (!track.artworkUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkGrey)
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        // Title and Artist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) SystemAccentBlue else PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MutedGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        val durationFormatted = formatDuration(track.durationMs)
        if (durationFormatted.isNotBlank()) {
            Text(
                text = durationFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MutedGrey,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // 3-dot More button
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = "More",
                tint = PureWhite.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
