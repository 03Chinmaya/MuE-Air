package com.mue.feature.player

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mue.core.design.PureWhite
import com.mue.core.design.applePressEffect
import com.mue.core.design.liquidGlass
import com.mue.core.design.sharedArtworkElement
import dev.chrisbanes.haze.HazeState
import com.mue.core.model.Track

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    trackId: String = "",
    title: String,
    artist: String,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    onPlayPause: () -> Unit,
    onNext: () -> Unit = {},
    onClick: () -> Unit = {},
    artworkUrl: String? = null,
    youtubeVideoId: String? = null,
    track: Track? = null,
    onShare: ((Track) -> Unit)? = null,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(artworkUrl) {
        if (!artworkUrl.isNullOrEmpty()) {
            try {
                val imageLoader = SingletonImageLoader.get(context)
                // Prefetch high-res variant for FullPlayer transition
                val highResUrl = when {
                    artworkUrl.contains("150x150") -> artworkUrl.replace("150x150", "500x500")
                    artworkUrl.contains("hqdefault") -> artworkUrl.replace("hqdefault", "maxresdefault")
                    else -> null
                }
                if (!highResUrl.isNullOrBlank() && highResUrl != artworkUrl) {
                    imageLoader.enqueue(ImageRequest.Builder(context).data(highResUrl).build())
                }
            } catch (_: Throwable) {
                // Fallback gracefully
            }
        }
    }

    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .applePressEffect()
            .liquidGlass(hazeState = hazeState, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .sharedArtworkElement(key = "artwork_$trackId"),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            val trackToShare = track ?: Track(
                                id = trackId,
                                title = title,
                                artist = artist,
                                album = "",
                                durationMs = durationMs,
                                artworkUrl = artworkUrl,
                                youtubeVideoId = youtubeVideoId,
                                provider = com.mue.core.model.ProviderId.JIOSAAVN
                            )
                            if (onShare != null) {
                                onShare(trackToShare)
                            } else {
                                val resolvedYtId = youtubeVideoId ?: (if (trackId.isNotBlank() && trackId.length in 10..12) trackId else null)
                                val shareText = if (resolvedYtId != null) {
                                    "https://music.youtube.com/watch?v=$resolvedYtId"
                                } else {
                                    "Listening to $title by $artist on MuE"
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    putExtra(Intent.EXTRA_SUBJECT, "$title — $artist")
                                }
                                val chooserIntent = Intent.createChooser(sendIntent, "Share track")
                                context.startActivity(chooserIntent)
                            }
                        }
                    )
            ) {
                Text(title, color = PureWhite, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(artist, color = PureWhite.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }

            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = PureWhite
                )
            }

            IconButton(onClick = onNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = PureWhite
                )
            }
        }

        // Custom BitChord thin progress bar at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomStart)
                .background(PureWhite.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .height(2.dp)
                    .background(PureWhite)
            )
        }
    }
}
