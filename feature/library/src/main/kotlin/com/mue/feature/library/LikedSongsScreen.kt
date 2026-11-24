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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.mue.core.design.SystemAccentBlue
import com.mue.core.design.liquidGlass

@Composable
fun LikedSongsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val domainQueue = likedSongs.map { it.toTrack() }

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
                    text = "Liked Songs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
                Text(
                    text = "${likedSongs.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGrey
                )
            }
        }

        // Action Buttons Row (Play & Shuffle)
        if (likedSongs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play All Button
                Button(
                    onClick = { viewModel.playQueue(domainQueue, shuffle = false, source = "Liked Songs") },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SystemAccentBlue)
                ) {
                    Icon(
                        imageVector = SfIcons.Play,
                        contentDescription = "Play All",
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play",
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                }

                // Shuffle Button
                Button(
                    onClick = { viewModel.playQueue(domainQueue, shuffle = true, source = "Liked Songs") },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .liquidGlass(cornerRadius = 22.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Icon(
                        imageVector = SfIcons.Shuffle,
                        contentDescription = "Shuffle",
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shuffle",
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                }
            }
        }

        if (likedSongs.isEmpty()) {
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
                        text = "No Liked Songs Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the heart icon on any song to save it here for instant offline-first listening.",
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
                itemsIndexed(likedSongs, key = { _, s -> s.songId }) { index, song ->
                    ListRow(
                        title = song.title,
                        subtitle = song.artist,
                        artworkUrl = song.artworkUrl,
                        onClick = {
                            viewModel.playTrack(song.toTrack(), domainQueue, source = "Liked Songs")
                        }
                    )
                }
            }
        }
    }
}

