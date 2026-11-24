package com.mue.feature.search

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mue.core.design.DarkGrey
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureWhite
import com.mue.core.design.SearchPlayingBars
import com.mue.core.design.applePressEffect
import com.mue.core.model.Track

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchMediaRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onClickMore: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val cornerShape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .applePressEffect()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(cornerShape)
                .background(DarkGrey)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.artworkUrl)
                    .crossfade(true)
                    .size(128)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(DarkGrey.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    SearchPlayingBars()
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val durationStr = formatDuration(track.durationMs)
            val subtitleText = if (durationStr.isNotBlank()) {
                "Song • ${track.artist} • $durationStr"
            } else {
                "Song • ${track.artist}"
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isPlaying) {
            SearchPlayingBars(
                modifier = Modifier.padding(end = 8.dp)
            )
        } else if (onClickMore != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .applePressEffect()
                    .clickable(onClick = onClickMore),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = MutedGrey,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

