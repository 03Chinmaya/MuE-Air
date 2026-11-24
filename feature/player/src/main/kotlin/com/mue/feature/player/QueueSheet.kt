package com.mue.feature.player

import android.content.Intent
import android.media.AudioDeviceInfo
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mue.core.design.DarkGrey
import com.mue.core.design.LocalHazeState
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.ThinSlider
import com.mue.core.design.liquidGlass
import com.mue.core.model.ProviderId
import com.mue.core.model.Track

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun AnimatedPlayingBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = PureWhite
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PlayingBars")
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar3"
    )

    Row(
        modifier = modifier.height(16.dp).width(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(bar1Height, bar2Height, bar3Height).forEach { scale ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(if (isPlaying) scale else 0.35f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun ReorderHandle(
    modifier: Modifier = Modifier,
    color: Color = MutedGrey
) {
    Column(
        modifier = modifier.width(16.dp).height(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
    }
}

private val defaultAutoPlayTracks = listOf(
    Track(
        id = "autoplay_1",
        title = "Tainu Khabar Nahi (From \"Munjya\")",
        artist = "Sachin-Jigar, Arijit Singh & Amitabh Bhattacharya",
        album = "Munjya",
        durationMs = 210000L,
        artworkUrl = "https://c.saavncdn.com/978/Munjya-Hindi-2024-20240527181005-500x500.jpg",
        provider = ProviderId.JIOSAAVN
    ),
    Track(
        id = "autoplay_2",
        title = "Kaahe Mose",
        artist = "Garvit Soni, Priyansh Srivastava & Garvit-Priyansh",
        album = "Kaahe Mose",
        durationMs = 195000L,
        artworkUrl = "https://c.saavncdn.com/391/Kaahe-Mose-Hindi-2023-20230623053825-500x500.jpg",
        provider = ProviderId.JIOSAAVN
    ),
    Track(
        id = "autoplay_3",
        title = "Ishq Di Baajiyaan",
        artist = "Shankar Ehsaan Loy & Diljit Dosanjh",
        album = "Soorma",
        durationMs = 240000L,
        artworkUrl = "https://c.saavncdn.com/152/Soorma-Hindi-2018-20180709-500x500.jpg",
        provider = ProviderId.JIOSAAVN
    ),
    Track(
        id = "autoplay_4",
        title = "Tenu Chad Ke (Samjhawan - Trending Version)",
        artist = "Jawad Ahmed, Sharib Toshi, Shreya Ghoshal",
        album = "Trending Version",
        durationMs = 225000L,
        artworkUrl = "https://c.saavncdn.com/743/Samjhawan-From-Humpty-Sharma-Ki-Dulhania--Hindi-2014-500x500.jpg",
        provider = ProviderId.JIOSAAVN
    )
)

@Composable
fun QueueSheet(
    queue: List<Track>,
    currentTrackId: String?,
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    currentLyricLine: String? = null,
    onSeekTo: (Long) -> Unit = {},
    onPlayPause: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onTrackSelect: (Track) -> Unit,
    onRemoveFromQueue: ((Track) -> Unit)? = null,
    onOpenLyrics: () -> Unit = {},
    onListenTogether: (() -> Unit)? = null,
    audioRoute: String = "This phone",
    onSelectAudioDevice: ((AudioDeviceInfo?) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) {
        onDismiss()
    }

    var showMenu by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }

    // Derive active playing track
    val activeTrack = currentTrack ?: queue.firstOrNull { it.id == currentTrackId } ?: queue.firstOrNull()

    // Up next tracks excluding current track
    val upcomingQueue = queue.filter { it.id != activeTrack?.id }
    val displayedAutoPlay = if (upcomingQueue.isNotEmpty()) upcomingQueue else defaultAutoPlayTracks

    Box(
        modifier = Modifier
            .fillMaxSize()
            .liquidGlass(
                hazeState = LocalHazeState.current,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Drag Handle
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f))
                    .clickable { onDismiss() }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Top Section: Small artwork, title, artist, 3-dot menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF242426)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!activeTrack?.artworkUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(activeTrack?.artworkUrl)
                                .size(128)
                                .crossfade(true)
                                .build(),
                            contentDescription = activeTrack?.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MutedGrey,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeTrack?.title ?: "No track playing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeTrack?.artist ?: "Unknown artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E))
                        .clickable { showMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Options",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear Queue") },
                            onClick = {
                                showMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Header text: "Queue"
            Text(
                text = "Queue",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Now Playing Section
            Text(
                text = "Now playing",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E20).copy(alpha = 0.6f))
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2C)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!activeTrack?.artworkUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(activeTrack?.artworkUrl)
                                .size(128)
                                .crossfade(true)
                                .build(),
                            contentDescription = activeTrack?.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MutedGrey,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeTrack?.title ?: "Track Title",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeTrack?.artist ?: "Artist Name",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AnimatedPlayingBars(
                    isPlaying = isPlaying,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        activeTrack?.let { onRemoveFromQueue?.invoke(it) }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MutedGrey,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. AutoPlay Section
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AutoPlay",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
            }
            Text(
                text = "Similar music, selected to play next",
                style = MaterialTheme.typography.bodySmall,
                color = MutedGrey,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Upcoming Tracks List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(displayedAutoPlay, key = { _, item -> item.id }) { _, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onTrackSelect(track) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Drag handle
                        ReorderHandle(modifier = Modifier.padding(end = 12.dp, start = 4.dp))

                        // Artwork
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF242426)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!track.artworkUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(track.artworkUrl)
                                        .size(128)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = track.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MutedGrey,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title & Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Remove X Button
                        IconButton(
                            onClick = { onRemoveFromQueue?.invoke(track) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MutedGrey,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 5. Lyrics Preview Row
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenLyrics() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentLyricLine ?: "Jo tum mere ho",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = PureWhite.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Seekbar
            ThinSlider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                onValueChange = { frac ->
                    val target = (frac * durationMs).toLong()
                    onSeekTo(target)
                },
                onValueChangeFinished = {},
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGrey,
                    fontSize = 12.sp
                )
                val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
                Text(
                    text = "-${formatTime(remainingMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGrey,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 7. Transport Controls: Previous, Play/Pause, Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = PureWhite,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(modifier = Modifier.width(36.dp))
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = PureWhite,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.width(36.dp))
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = PureWhite,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 8. Bottom Dock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenLyrics) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Subject,
                        contentDescription = "Lyrics",
                        tint = PureWhite.copy(alpha = 0.75f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Center floating glass pill with Audio Output Wave & Listen Together
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF242426).copy(alpha = 0.85f))
                        .liquidGlass(cornerRadius = 32.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Audio Output",
                            tint = PureWhite.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(Color(0xFF38383A))
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onListenTogether?.invoke() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = com.mue.core.design.R.drawable.ic_listen_together),
                            contentDescription = "Listen Together",
                            tint = PureWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Queue icon with active circular highlight in QueueSheet
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38383A).copy(alpha = 0.8f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Close Queue",
                        tint = PureWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = audioRoute,
                style = MaterialTheme.typography.bodySmall,
                color = MutedGrey,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        showAudioOutputSheet = true
                    }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Native Audio Output Switcher Bottom Sheet
        if (showAudioOutputSheet) {
            AudioOutputSheet(
                currentRoute = audioRoute,
                onSelectDevice = { device ->
                    onSelectAudioDevice?.invoke(device)
                    showAudioOutputSheet = false
                },
                onDismiss = { showAudioOutputSheet = false }
            )
        }
    }
}
