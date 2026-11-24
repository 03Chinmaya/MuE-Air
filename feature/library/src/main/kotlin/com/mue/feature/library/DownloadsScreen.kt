package com.mue.feature.library

import androidx.compose.ui.draw.alpha
import com.mue.core.design.MuELogoWhite
import com.mue.core.design.LayoutTokens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mue.core.database.toTrack
import com.mue.core.design.ListRow
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SfIcons
import java.util.Locale

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloadedSongs.collectAsStateWithLifecycle()
    val domainQueue = downloads.map { it.toTrack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
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
            Column {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
                Text(
                    text = "${downloads.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGrey
                )
            }
        }

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MuELogoWhite(
                        size = 72.dp,
                        modifier = Modifier.alpha(0.25f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Downloaded Tracks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Downloaded songs will be saved here for offline playback with zero latency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LayoutTokens.ListBottomPaddingWithMiniPlayer),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(downloads, key = { _, d -> d.songId }) { index, item ->
                    val sizeStr = formatFileSize(item.fileSize)
                    val subtitle = if (sizeStr.isNotEmpty()) "${item.artist} • $sizeStr" else item.artist
                    ListRow(
                        title = item.title,
                        subtitle = subtitle,
                        artworkUrl = item.artworkUrl,
                        onClick = {
                            viewModel.playTrack(item.toTrack(), domainQueue, source = "Downloads")
                        }
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", kb)
    }
}

