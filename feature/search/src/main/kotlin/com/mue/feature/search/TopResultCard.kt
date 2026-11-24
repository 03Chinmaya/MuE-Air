package com.mue.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mue.core.design.DarkGrey
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.applePressEffect
import com.mue.core.model.Track

@Composable
fun TopResultCard(
    topResult: TopResult,
    onPlayTrack: (Track) -> Unit,
    onAddTrack: (Track) -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {}
) {
    when (topResult) {
        is TopResult.TrackResult -> {
            TopResultTrackContent(
                track = topResult.track,
                onPlay = { onPlayTrack(topResult.track) },
                onAdd = { onAddTrack(topResult.track) },
                modifier = modifier
            )
        }
        is TopResult.ArtistResult -> {
            val a = topResult.artist
            TopResultArtistContent(
                artist = a,
                onClick = {
                    val name = android.net.Uri.encode(a.name)
                    val subs = a.subscribers?.let { android.net.Uri.encode(it) } ?: ""
                    val art = a.artworkUrl?.let { android.net.Uri.encode(it) } ?: ""
                    val route = "artist/${a.id}?name=$name&subs=$subs&art=$art"
                    onArtistClick(route)
                },
                modifier = modifier
            )
        }
        is TopResult.AlbumResult -> {
            val album = topResult.album
            TopResultAlbumContent(
                album = album,
                onClick = { onAlbumClick(album.id ?: "") },
                onPlay = { onPlayTrack(album.toTrack()) },
                modifier = modifier
            )
        }
    }
}

/**
 * Backward compatibility overload for Track-only callers
 */
@Composable
fun TopResultCard(
    track: Track,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopResultTrackContent(
        track = track,
        onPlay = onPlay,
        onAdd = onAdd,
        modifier = modifier
    )
}

@Composable
private fun TopResultTrackContent(
    track: Track,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerShape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkGrey.copy(alpha = 0.5f))
            .applePressEffect()
            .clickable(onClick = onPlay)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Top result",
                style = MaterialTheme.typography.labelLarge,
                color = MutedGrey,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(PureWhite.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Song",
                    style = MaterialTheme.typography.labelSmall,
                    color = PureWhite,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(cornerShape)
                    .background(DarkGrey)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(track.artworkUrl)
                        .crossfade(true)
                        .size(256)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${track.artist}${if (!track.album.isNullOrBlank()) " • ${track.album}" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Play Button: White background, Black text
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(PureWhite)
                    .applePressEffect()
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = PureBlack,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.labelLarge,
                    color = PureBlack,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Add Button: DarkGrey background, White text
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(DarkGrey)
                    .applePressEffect()
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add",
                    tint = PureWhite,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add",
                    style = MaterialTheme.typography.labelLarge,
                    color = PureWhite,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TopResultArtistContent(
    artist: com.mue.core.network.YtmArtistSearchItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkGrey.copy(alpha = 0.5f))
            .applePressEffect()
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Top result",
                style = MaterialTheme.typography.labelLarge,
                color = MutedGrey,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(PureWhite.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Artist",
                    style = MaterialTheme.typography.labelSmall,
                    color = PureWhite,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DarkGrey),
                contentAlignment = Alignment.Center
            ) {
                if (!artist.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artist.artworkUrl)
                            .crossfade(true)
                            .size(256)
                            .build(),
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = artist.name,
                        tint = PureWhite.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artist.subscribers ?: "Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(PureWhite)
                    .applePressEffect()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = "View Artist",
                    tint = PureBlack,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "View Artist",
                    style = MaterialTheme.typography.labelLarge,
                    color = PureBlack,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TopResultAlbumContent(
    album: com.mue.core.providers.YtmAlbumDto,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerShape = RoundedCornerShape(10.dp)
    val title = album.title ?: album.name ?: "Album"
    val subtitle = album.subtitle ?: (album.year?.let { "$it" } ?: "Album")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkGrey.copy(alpha = 0.5f))
            .applePressEffect()
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Top result",
                style = MaterialTheme.typography.labelLarge,
                color = MutedGrey,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(PureWhite.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Album",
                    style = MaterialTheme.typography.labelSmall,
                    color = PureWhite,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(cornerShape)
                    .background(DarkGrey)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(album.artworkUrl)
                        .crossfade(true)
                        .size(256)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(PureWhite)
                    .applePressEffect()
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = PureBlack,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.labelLarge,
                    color = PureBlack,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
