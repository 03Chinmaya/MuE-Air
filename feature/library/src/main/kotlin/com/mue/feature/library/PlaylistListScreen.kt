package com.mue.feature.library

import androidx.compose.ui.draw.alpha
import com.mue.core.design.MuELogoWhite
import com.mue.core.design.LayoutTokens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mue.core.design.DarkGrey
import com.mue.core.design.GlassAlertDialog
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SfIcons
import com.mue.core.design.SystemAccentBlue
import com.mue.core.design.applePressEffect
import com.mue.core.design.liquidGlass

@Composable
fun PlaylistListScreen(
    playlists: List<UnifiedPlaylist>,
    onBack: () -> Unit,
    onPlay: (id: String, isImported: Boolean) -> Unit,
    onCreatePlaylist: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                    Text(
                        text = "${playlists.size} personal & imported mixes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey
                    )
                }
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Playlist",
                        tint = PureWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MuELogoWhite(
                            size = 72.dp,
                            modifier = Modifier.alpha(0.25f)
                        )
                        Text(
                            text = "No Playlists Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                        Text(
                            text = "Tap + to create a playlist or import by pasting a link in Search",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGrey,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = LayoutTokens.ListBottomPaddingWithMiniPlayer),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(playlists, key = { "${if (it.isImported) "imp_" else "loc_"}${it.id}" }) { playlist ->
                        UnifiedPlaylistRow(
                            playlist = playlist,
                            onClick = { onPlay(playlist.id, playlist.isImported) }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = SystemAccentBlue,
            contentColor = PureWhite,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 110.dp, end = 20.dp)
                .applePressEffect()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Playlist",
                modifier = Modifier.size(24.dp)
            )
        }

        // Create Playlist Dialog
        if (showCreateDialog) {
            GlassAlertDialog(
                onDismissRequest = {
                    showCreateDialog = false
                    newPlaylistName = ""
                },
                title = {
                    Text(
                        text = "New Playlist",
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Give your playlist a title.",
                            color = MutedGrey,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        BasicTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = PureWhite,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(PureWhite),
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(cornerRadius = 12.dp)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreatePlaylist(newPlaylistName.trim())
                            }
                            showCreateDialog = false
                            newPlaylistName = ""
                        }
                    ) {
                        Text("Create", color = SystemAccentBlue, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateDialog = false
                            newPlaylistName = ""
                        }
                    ) {
                        Text("Cancel", color = MutedGrey)
                    }
                }
            )
        }
    }
}

@Composable
private fun UnifiedPlaylistRow(
    playlist: UnifiedPlaylist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .applePressEffect()
            .liquidGlass(cornerRadius = 18.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!playlist.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = playlist.artworkUrl,
                contentDescription = playlist.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = SfIcons.Playlists,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sourceLabel = when {
                    playlist.source.equals("spotify", ignoreCase = true) -> "Spotify"
                    playlist.source.equals("youtube", ignoreCase = true) -> "YouTube Music"
                    else -> "Personal"
                }
                Text(
                    text = "$sourceLabel • ${if (playlist.trackCount > 0) "${playlist.trackCount} tracks" else "Mix"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGrey
                )
            }
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = SfIcons.Play,
                contentDescription = "Play Playlist",
                tint = PureWhite,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
