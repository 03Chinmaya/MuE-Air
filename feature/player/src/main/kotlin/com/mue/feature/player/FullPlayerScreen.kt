@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mue.feature.player

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioDeviceInfo
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.mue.core.database.PlaylistEntity
import com.mue.core.design.BrandDrawables
import com.mue.core.design.AccentBlue
import com.mue.core.design.DarkGrey
import com.mue.core.design.LocalHazeState
import com.mue.core.design.LocalHighPerformanceMode
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SurfaceElevated
import com.mue.core.design.ThinSlider
import com.mue.core.design.liquidGlass
import com.mue.core.design.sharedArtworkElement
import com.mue.core.lyrics.LyricsRepository
import com.mue.core.lyrics.model.TrackLyrics
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun FullPlayerScreen(
    trackId: String,
    title: String,
    artist: String,
    artworkUrl: String?,
    animatedArtworkUrl: String? = null,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    queue: List<Track> = emptyList(),
    lyricsRepository: LyricsRepository? = null,
    currentLyricLine: String? = null,
    onSeekTo: (Long) -> Unit = {},
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    isShuffleEnabled: Boolean = false,
    isRepeatEnabled: Boolean = false,
    onTrackSelect: (Track) -> Unit = {},
    onRemoveFromQueue: ((Track) -> Unit)? = null,
    onListenTogether: (() -> Unit)? = null,
    audioRoute: String = "This phone",
    track: Track? = null,
    youtubeVideoId: String? = null,
    onShare: ((Track) -> Unit)? = null,
    playingFrom: String = "Unknown",
    onGoToArtist: ((String) -> Unit)? = null,
    onSaveToLibrary: ((Track) -> Unit)? = null,
    isLiked: Boolean = false,
    onToggleLike: ((Track) -> Unit)? = null,
    playlists: List<PlaylistEntity> = emptyList(),
    onAddToPlaylist: ((Track, String) -> Unit)? = null,
    onCreatePlaylistAndAdd: ((String, Track) -> Unit)? = null,
    onSetSleepTimer: ((Int?) -> Unit)? = null,
    onSetPlaybackSpeed: ((Float) -> Unit)? = null,
    onSelectAudioDevice: ((AudioDeviceInfo?) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val highPerformance = LocalHighPerformanceMode.current
    val activeTrack = track ?: queue.find { it.id == trackId }

    var dominantColor by remember { mutableStateOf(DarkGrey) }
    var vibrantColor by remember { mutableStateOf(SurfaceElevated) }
    var darkVibrantColor by remember { mutableStateOf(PureBlack) }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    val activeArtwork = animatedArtworkUrl ?: artworkUrl

    LaunchedEffect(artworkUrl) {
        if (!artworkUrl.isNullOrEmpty()) {
            try {
                val imageLoader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.image.toBitmap()
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap).generate()
                    }
                    dominantColor = palette.dominantSwatch?.rgb?.let { Color(it) } ?: DarkGrey
                    vibrantColor = palette.vibrantSwatch?.rgb?.let { Color(it) }
                        ?: palette.lightVibrantSwatch?.rgb?.let { Color(it) }
                        ?: SurfaceElevated
                    darkVibrantColor = palette.darkVibrantSwatch?.rgb?.let { Color(it) }
                        ?: palette.darkMutedSwatch?.rgb?.let { Color(it) }
                        ?: PureBlack
                }
            } catch (_: Throwable) {
                // Fallback gracefully
            }
        }
    }

    val animatedDominant by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(600),
        label = "DominantColor"
    )
    val animatedVibrant by animateColorAsState(
        targetValue = vibrantColor,
        animationSpec = tween(600),
        label = "VibrantColor"
    )
    val animatedDarkVibrant by animateColorAsState(
        targetValue = darkVibrantColor,
        animationSpec = tween(600),
        label = "DarkVibrantColor"
    )

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "DragOffset"
    )

    // Animated Ambient Mesh Orbs
    val transition = rememberInfiniteTransition(label = "MeshTransition")
    val orb1Offset by transition.animateFloat(
        initialValue = -40f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Orb1Offset"
    )
    val orb2Offset by transition.animateFloat(
        initialValue = 50f,
        targetValue = -50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Orb2Offset"
    )

    // Live lyric computation
    val lyricsFlow = remember(trackId) {
        lyricsRepository?.getLyrics(trackId, title, artist)
    }
    val trackLyrics by (lyricsFlow ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(initial = null)

    val currentTrackObj = remember(trackId, title, artist, artworkUrl, durationMs) {
        Track(
            id = trackId,
            title = title,
            artist = artist,
            album = "",
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            provider = com.mue.core.model.ProviderId.JIOSAAVN
        )
    }

    BackHandler(enabled = true) {
        if (showQueueSheet) {
            showQueueSheet = false
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    if (delta > 0 || offsetY > 0) {
                        offsetY = (offsetY + delta).coerceAtLeast(0f)
                    }
                },
                onDragStopped = {
                    if (offsetY > 300f) {
                        onDismiss()
                    } else {
                        offsetY = 0f
                    }
                }
            )
            .background(PureBlack)
    ) {
        // Ambient Mesh Gradient Background
        Box(modifier = Modifier.fillMaxSize()) {
            if (!activeArtwork.isNullOrEmpty()) {
                AsyncImage(
                    model = activeArtwork,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 70.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = BrandDrawables.watermark),
                    contentDescription = null,
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.Center)
                        .alpha(0.08f),
                    contentScale = ContentScale.Fit
                )
            }

            // Ambient gradient orbs
            Box(
                modifier = Modifier
                    .size(360.dp)
                    .offset(x = (-60).dp, y = (orb1Offset + 80).dp)
                    .blur(80.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedVibrant.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Box(
                modifier = Modifier
                    .size(380.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 80.dp, y = (orb2Offset - 100).dp)
                    .blur(90.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedDominant.copy(alpha = 0.50f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Dark gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                animatedDarkVibrant.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }

        // Main Player Column Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Centered drag handle and "Playing from Search"
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f))
                    .clickable { onDismiss() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            val headerText = if (playingFrom != "Unknown" && playingFrom.isNotBlank()) {
                if (playingFrom.startsWith("Playing from", ignoreCase = true)) playingFrom else "Playing from $playingFrom"
            } else {
                "Now Playing"
            }
            Text(
                text = headerText,
                style = MaterialTheme.typography.bodySmall,
                color = PureWhite.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.weight(0.12f))

            val coroutineScope = rememberCoroutineScope()
            val heartScale = remember { Animatable(0.5f) }
            val heartAlpha = remember { Animatable(0f) }

            // Artwork: Large square artwork with rounded corners, centered
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .pointerInput(activeTrack?.id ?: trackId) {
                        detectTapGestures(
                            onDoubleTap = {
                                activeTrack?.let { currentTrack ->
                                    onToggleLike?.invoke(currentTrack)
                                    coroutineScope.launch {
                                        heartScale.snapTo(0.5f)
                                        heartAlpha.snapTo(1f)
                                        launch {
                                            heartScale.animateTo(
                                                targetValue = 1.25f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            )
                                            heartScale.animateTo(
                                                targetValue = 1.0f,
                                                animationSpec = tween(150)
                                            )
                                        }
                                        launch {
                                            delay(450)
                                            heartAlpha.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(350)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!activeArtwork.isNullOrEmpty()) {
                    AsyncImage(
                        model = activeArtwork,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = BrandDrawables.watermark),
                        contentDescription = null,
                        modifier = Modifier
                            .size(220.dp)
                            .alpha(0.08f),
                        contentScale = ContentScale.Fit
                    )
                }

                if (heartAlpha.value > 0f) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFA2D48),
                        modifier = Modifier
                            .size(88.dp)
                            .graphicsLayer {
                                scaleX = heartScale.value
                                scaleY = heartScale.value
                                alpha = heartAlpha.value
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // Track Info Row: Title & Artist on Left, Like + 3-dot Menu on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MutedGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Clickable lyrics preview directly below Title & Artist
                    LyricsPreviewRow(
                        trackLyrics = trackLyrics,
                        positionMs = positionMs,
                        currentLyricLine = currentLyricLine,
                        onClick = {
                            if (lyricsRepository == null) {
                                android.widget.Toast.makeText(context, "Lyrics service unavailable", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                showLyricsSheet = true
                            }
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Like / Heart button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E))
                            .clickable {
                                activeTrack?.let { onToggleLike?.invoke(it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isLiked) "Unlike" else "Like",
                            tint = if (isLiked) Color(0xFFFA2D48) else PureWhite.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3-dot circular button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E))
                            .clickable { showMoreMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More",
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Seekbar: ThinSlider with elapsed time & negative remaining time (NO volume slider)
            PlayerProgressSection(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekTo = onSeekTo,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(0.1f))

            // Transport Controls: Shuffle, Previous, Play/Pause, Next, Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) AccentBlue else PureWhite.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = PureWhite,
                        modifier = Modifier.size(42.dp)
                    )
                }
                // Play / Pause (largest)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = PureWhite,
                        modifier = Modifier.size(56.dp)
                    )
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = PureWhite,
                        modifier = Modifier.size(42.dp)
                    )
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeatEnabled) AccentBlue else PureWhite.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Bottom Dock (New): Quote icon, Glass pill with L-T & User, Queue icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (lyricsRepository == null) {
                        android.widget.Toast.makeText(context, "Lyrics service unavailable", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        showLyricsSheet = true
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Subject,
                        contentDescription = "Lyrics",
                        tint = PureWhite.copy(alpha = 0.75f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Center floating glass pill: Audio Output Wave (Left) & Listen Together (Right)
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .liquidGlass(
                            hazeState = LocalHazeState.current,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                showAudioOutputSheet = true
                            },
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
                            .height(20.dp)
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

                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = PureWhite.copy(alpha = 0.75f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = audioRoute,
                style = MaterialTheme.typography.bodySmall,
                color = MutedGrey,
                fontSize = 11.sp,
                modifier = Modifier.clickable {
                    showAudioOutputSheet = true
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Lyrics Bottom Sheet Modal
        if (showLyricsSheet && lyricsRepository != null) {
            LyricsBottomSheet(
                trackId = trackId,
                title = title,
                artist = artist,
                positionMs = positionMs,
                lyricsRepository = lyricsRepository,
                onSeekTo = onSeekTo,
                onDismiss = { showLyricsSheet = false }
            )
        }

        // Queue Sheet Full Modal Overlay
        AnimatedVisibility(
            visible = showQueueSheet,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            )
        ) {
            QueueSheet(
                queue = queue,
                currentTrackId = trackId,
                currentTrack = currentTrackObj,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                currentLyricLine = currentLyricLine ?: trackLyrics?.lines?.firstOrNull { positionMs in it.lineStartMs..it.lineEndMs }?.text ?: "",
                onSeekTo = onSeekTo,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onTrackSelect = onTrackSelect,
                onRemoveFromQueue = onRemoveFromQueue,
                onOpenLyrics = {
                    showQueueSheet = false
                    showLyricsSheet = true
                },
                onListenTogether = onListenTogether,
                audioRoute = audioRoute,
                onSelectAudioDevice = onSelectAudioDevice,
                onDismiss = { showQueueSheet = false }
            )
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

        // 3-Dot More Actions Bottom Sheet Modal
        if (showMoreMenu) {
            ModalBottomSheet(
                onDismissRequest = { showMoreMenu = false },
                containerColor = Color.Transparent,
                dragHandle = null,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .liquidGlass(LocalHazeState.current, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(vertical = 16.dp, horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Small drag pill indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(36.dp)
                                .height(4.dp)
                                .background(PureWhite.copy(alpha = 0.2f), CircleShape)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Track info header in sheet
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 1. View Queue
                        PlayerMenuItem(
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            title = "View Queue",
                            onClick = {
                                showMoreMenu = false
                                showQueueSheet = true
                            }
                        )

                        // 2. View Lyrics
                        PlayerMenuItem(
                            icon = Icons.AutoMirrored.Filled.Subject,
                            title = "View Lyrics",
                            onClick = {
                                showMoreMenu = false
                                if (lyricsRepository == null) {
                                    android.widget.Toast.makeText(context, "Lyrics service unavailable", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    showLyricsSheet = true
                                }
                            }
                        )

                        // 3. Share
                        PlayerMenuItem(
                            icon = Icons.Default.Share,
                            title = "Share",
                            onClick = {
                                showMoreMenu = false
                                val trackToShare = activeTrack ?: currentTrackObj
                                if (onShare != null) {
                                    onShare(trackToShare)
                                } else {
                                    val resolvedYtId = youtubeVideoId ?: activeTrack?.youtubeVideoId ?: (if (activeTrack?.provider == ProviderId.YOUTUBE_MUSIC) trackId else null)
                                    val shareText = if (resolvedYtId != null) {
                                        "https://music.youtube.com/watch?v=$resolvedYtId"
                                    } else {
                                        "Listening to $title by $artist on MuE"
                                    }
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        putExtra(Intent.EXTRA_SUBJECT, "$title — $artist")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share track").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                }
                            }
                        )

                        // 4. Sleep Timer
                        PlayerMenuItem(
                            icon = Icons.Default.Timer,
                            title = "Sleep Timer",
                            onClick = {
                                showMoreMenu = false
                                showSleepTimerDialog = true
                            }
                        )

                        // 5. Playback Speed
                        PlayerMenuItem(
                            icon = Icons.Default.Speed,
                            title = "Playback Speed",
                            onClick = {
                                showMoreMenu = false
                                showSpeedDialog = true
                            }
                        )

                        // 6. Go to Artist
                        PlayerMenuItem(
                            icon = Icons.Default.Person,
                            title = "Go to Artist",
                            onClick = {
                                showMoreMenu = false
                                if (onGoToArtist != null) {
                                    onGoToArtist(artist)
                                }
                            }
                        )

                        // 7. Save to Library
                        PlayerMenuItem(
                            icon = Icons.Default.BookmarkAdd,
                            title = "Save to Library",
                            onClick = {
                                showMoreMenu = false
                                val trackToSave = activeTrack ?: currentTrackObj
                                onSaveToLibrary?.invoke(trackToSave)
                                android.widget.Toast.makeText(context, "Saved to Liked Songs", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )

                        // 8. Add to Playlist
                        PlayerMenuItem(
                            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                            title = "Add to Playlist",
                            onClick = {
                                showMoreMenu = false
                                showPlaylistPicker = true
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        // Sleep Timer Dialog
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                title = { Text("Sleep Timer", color = PureWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            15 to "15 minutes",
                            30 to "30 minutes",
                            45 to "45 minutes",
                            60 to "60 minutes",
                            -1 to "End of track",
                            null to "Turn off timer"
                        ).forEach { (minutes, label) ->
                            Text(
                                text = label,
                                color = PureWhite,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onSetSleepTimer?.invoke(minutes)
                                        showSleepTimerDialog = false
                                        val msg = if (minutes == null) "Sleep timer turned off" else "Sleep timer set for $label"
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Cancel", color = MutedGrey)
                    }
                },
                containerColor = Color(0xFF1C1C1E)
            )
        }

        // Playback Speed Dialog
        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text("Playback Speed", color = PureWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val label = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x"
                            Text(
                                text = label,
                                color = PureWhite,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onSetPlaybackSpeed?.invoke(speed)
                                        showSpeedDialog = false
                                        android.widget.Toast.makeText(context, "Playback speed set to $label", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text("Cancel", color = MutedGrey)
                    }
                },
                containerColor = Color(0xFF1C1C1E)
            )
        }

        // Playlist Picker Bottom Sheet Modal
        if (showPlaylistPicker) {
            val targetTrack = activeTrack ?: currentTrackObj
            PlaylistPickerSheet(
                track = targetTrack,
                playlists = playlists,
                onSelectPlaylist = { playlist ->
                    onAddToPlaylist?.invoke(targetTrack, playlist.id)
                    showPlaylistPicker = false
                    android.widget.Toast.makeText(context, "Added to ${playlist.name}", android.widget.Toast.LENGTH_SHORT).show()
                },
                onCreatePlaylist = { name ->
                    onCreatePlaylistAndAdd?.invoke(name, targetTrack)
                    showPlaylistPicker = false
                    android.widget.Toast.makeText(context, "Added to $name", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showPlaylistPicker = false }
            )
        }
    }
}

@Composable
private fun PlayerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = PureWhite,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = PureWhite,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PlayerProgressSection(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ThinSlider(
            value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
            onValueChange = { frac ->
                val target = (frac * durationMs).toLong()
                onSeekTo(target)
            },
            onValueChangeFinished = {},
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
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
    }
}

@Composable
private fun LyricsPreviewRow(
    trackLyrics: TrackLyrics?,
    positionMs: Long,
    currentLyricLine: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liveLyric = remember(trackLyrics, positionMs / 1000L) {
        val lines = trackLyrics?.lines.orEmpty()
        if (lines.isEmpty()) {
            null
        } else {
            val active = lines.firstOrNull { positionMs in it.lineStartMs..it.lineEndMs }
                ?: lines.lastOrNull { positionMs >= it.lineStartMs }
                ?: lines.firstOrNull()
            active?.text
        }
    }
    val displayLyric = currentLyricLine ?: liveLyric ?: ""

    if (displayLyric.isNotBlank()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayLyric,
                style = MaterialTheme.typography.bodyMedium,
                color = PureWhite.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Lyrics",
                tint = PureWhite.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
