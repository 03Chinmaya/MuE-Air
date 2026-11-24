package com.mue.feature.search

import com.mue.core.design.LayoutTokens

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mue.core.design.ArtistCircleCard
import com.mue.core.design.CoverCard
import com.mue.core.design.DarkGrey
import com.mue.core.design.MutedGrey
import com.mue.core.design.PillSegmentedControl
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SectionHeader
import com.mue.core.design.SfIcons
import com.mue.core.design.SongListSkeleton
import com.mue.core.design.VideoCarouselCard
import com.mue.core.design.VideoListRow
import com.mue.core.design.applePressEffect
import com.mue.core.design.liquidGlass
import com.mue.core.model.Track
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    onTrackClick: (Track) -> Unit = { viewModel.playTrack(it) },
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {}
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val allResults by viewModel.allResults.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val artistResults by viewModel.artistResults.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    val currentPlayingId = playerState.currentTrack?.id
    val isPlaying = playerState.isPlaying

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    selectedTrackForMenu?.let { track ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedTrackForMenu = null },
            sheetState = sheetState,
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF1C1C1E).copy(alpha = 0.95f))
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Track Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkGrey)
                        ) {
                            if (!track.artworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = track.artworkUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        thickness = 0.5.dp
                    )

                    // Action 1: Play Now
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .applePressEffect()
                            .clickable {
                                selectedTrackForMenu = null
                                onTrackClick(track)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = PureWhite,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Play Now",
                            color = PureWhite,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Action 2: Add to Queue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .applePressEffect()
                            .clickable {
                                viewModel.addToQueue(track)
                                selectedTrackForMenu = null
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = "Add to Queue",
                            tint = PureWhite,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Add to Queue",
                            color = PureWhite,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Action 3: Share (YTM Link)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .applePressEffect()
                            .clickable {
                                coroutineScope.launch {
                                    val url = viewModel.getShareUrl(track)
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist}")
                                        putExtra(Intent.EXTRA_TEXT, "${track.title} by ${track.artist}\n$url")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                                    selectedTrackForMenu = null
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = PureWhite,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Share YouTube Music Link",
                            color = PureWhite,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Pinned Header Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            SearchField(
                query = query,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSubmit = { viewModel.submitSearch() },
                modifier = Modifier.fillMaxWidth()
            )

            // Category Segmented Control (persistently visible when query is not blank)
            if (query.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                PillSegmentedControl(
                    options = SearchCategory.entries.map { it.label },
                    selectedIndex = selectedCategory.ordinal,
                    onSelect = { index ->
                        viewModel.onCategorySelect(SearchCategory.entries[index])
                    }
                )
            }
        }

        val hasActiveResults = when (selectedCategory) {
            SearchCategory.ALL -> {
                allResults.topResult != null ||
                allResults.songs.isNotEmpty() ||
                allResults.videos.isNotEmpty() ||
                allResults.albums.isNotEmpty() ||
                allResults.artists.isNotEmpty() ||
                allResults.playlists.isNotEmpty() ||
                searchResults.isNotEmpty()
            }
            SearchCategory.ARTISTS -> artistResults.isNotEmpty()
            else -> searchResults.isNotEmpty()
        }

        val emptyMessage = when (selectedCategory) {
            SearchCategory.ALL -> "No results found for \"$query\""
            SearchCategory.SONGS -> "No songs found for \"$query\""
            SearchCategory.VIDEOS -> "No videos found for \"$query\""
            SearchCategory.ALBUMS -> "No albums found for \"$query\""
            SearchCategory.ARTISTS -> "No artists found for \"$query\""
            SearchCategory.PLAYLISTS -> "No playlists found for \"$query\""
        }

        // Scrollable Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = LayoutTokens.ListBottomPaddingWithMiniPlayer),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when {
                // 1. Loading Skeleton State
                isLoading -> {
                    item(key = "search_skeleton") {
                        SongListSkeleton(
                            count = 6,
                            circular = selectedCategory == SearchCategory.ARTISTS
                        )
                    }
                }

                // 3. Empty Query State (Show Recent Searches or Placeholder)
                query.isBlank() -> {
                    if (recentSearches.isNotEmpty()) {
                        item(key = "recent_searches_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFFA2D48),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(percent = 50))
                                        .clickable { viewModel.clearHistory() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        items(recentSearches, key = { "recent_${it.id}_${it.timestamp}" }) { item ->
                            RecentSearchRow(
                                item = item,
                                onClick = { viewModel.onHistoryItemClick(item) },
                                onRemove = { viewModel.removeSearch(item.query) }
                            )
                        }
                    } else {
                        item(key = "empty_placeholder") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Search for songs, albums, or artists",
                                    color = MutedGrey,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                // 4. Results Empty State
                !hasActiveResults -> {
                    item(key = "no_results") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyMessage,
                                color = MutedGrey,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                // 5. Results Available: Categorized View for ALL
                selectedCategory == SearchCategory.ALL -> {
                    // Top Result Hero Card
                    allResults.topResult?.let { top ->
                        item(key = "all_top_result") {
                            TopResultCard(
                                topResult = top,
                                onPlayTrack = { track -> onTrackClick(track) },
                                onAddTrack = { track -> viewModel.addToQueue(track) },
                                onArtistClick = onNavigateToArtist,
                                onAlbumClick = { albumId -> onNavigateToAlbum("album/$albumId") }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // 1. Songs (compact vertical list matching YouTube Music)
                    if (allResults.songs.isNotEmpty()) {
                        item(key = "header_songs") {
                            SectionHeader(
                                title = "Songs",
                                onSeeAllClick = { viewModel.onCategorySelect(SearchCategory.SONGS) },
                                horizontalPadding = 0.dp
                            )
                        }
                        items(allResults.songs.take(4), key = { "all_song_${it.id}" }) { track ->
                            val isTrackPlaying = isPlaying && currentPlayingId == track.id
                            SearchMediaRow(
                                track = track,
                                isPlaying = isTrackPlaying,
                                onClick = { onTrackClick(track) },
                                onClickMore = { selectedTrackForMenu = track },
                                onLongClick = { selectedTrackForMenu = track },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item(key = "spacer_after_songs") {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // 2. Videos Carousel
                    if (allResults.videos.isNotEmpty()) {
                        item(key = "header_videos") {
                            SectionHeader(
                                title = "Videos",
                                onSeeAllClick = { viewModel.onCategorySelect(SearchCategory.VIDEOS) },
                                horizontalPadding = 0.dp
                            )
                        }
                        item(key = "carousel_videos") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(allResults.videos.take(10), key = { "video_${it.id}" }) { video ->
                                    VideoCarouselCard(
                                        title = video.title,
                                        channelName = video.artist,
                                        thumbnailUrl = video.artworkUrl,
                                        durationMs = video.durationMs,
                                        onClick = { onTrackClick(video) },
                                        width = 160.dp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // 3. Albums Carousel
                    if (allResults.albums.isNotEmpty()) {
                        item(key = "header_albums") {
                            SectionHeader(
                                title = "Albums",
                                onSeeAllClick = { viewModel.onCategorySelect(SearchCategory.ALBUMS) },
                                horizontalPadding = 0.dp
                            )
                        }
                        item(key = "carousel_albums") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(allResults.albums.take(10), key = { "album_${it.id ?: it.title}" }) { album ->
                                    CoverCard(
                                        title = album.title ?: album.name ?: "Album",
                                        subtitle = album.subtitle ?: (album.year?.let { "$it" } ?: "Album"),
                                        artworkUrl = album.artworkUrl,
                                        onClick = {
                                            val albumId = album.id ?: ""
                                            val title = android.net.Uri.encode(album.title ?: album.name ?: "")
                                            val art = android.net.Uri.encode(album.artworkUrl ?: "")
                                            val artist = android.net.Uri.encode(album.subtitle ?: "")
                                            val type = if (album.title?.contains("EP", ignoreCase = true) == true) "ep" else "album"
                                            onNavigateToAlbum("album/$albumId?title=$title&art=$art&artist=$artist&type=$type")
                                        },
                                        modifier = Modifier.size(150.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // 4. Artists Carousel
                    if (allResults.artists.isNotEmpty()) {
                        item(key = "header_artists") {
                            SectionHeader(
                                title = "Artists",
                                onSeeAllClick = { viewModel.onCategorySelect(SearchCategory.ARTISTS) },
                                horizontalPadding = 0.dp
                            )
                        }
                        item(key = "carousel_artists") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(allResults.artists.take(10), key = { "artist_${it.id}" }) { artist ->
                                    ArtistCircleCard(
                                        name = artist.name,
                                        imageUrl = artist.artworkUrl,
                                        role = artist.subscribers ?: "Artist",
                                        onClick = {
                                            val name = android.net.Uri.encode(artist.name)
                                            val subs = artist.subscribers?.let { android.net.Uri.encode(it) } ?: ""
                                            val art = artist.artworkUrl?.let { android.net.Uri.encode(it) } ?: ""
                                            val route = "artist/${artist.id}?name=$name&subs=$subs&art=$art"
                                            onNavigateToArtist(route)
                                        },
                                        modifier = Modifier.width(110.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // 5. Playlists Carousel
                    if (allResults.playlists.isNotEmpty()) {
                        item(key = "header_playlists") {
                            SectionHeader(
                                title = "Playlists",
                                onSeeAllClick = { viewModel.onCategorySelect(SearchCategory.PLAYLISTS) },
                                horizontalPadding = 0.dp
                            )
                        }
                        item(key = "carousel_playlists") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(allResults.playlists.take(10), key = { "playlist_${it.id ?: it.title}" }) { playlist ->
                                    CoverCard(
                                        title = playlist.title ?: playlist.name ?: "Playlist",
                                        subtitle = playlist.subtitle ?: playlist.author ?: "Community Playlist",
                                        artworkUrl = playlist.artworkUrl ?: playlist.thumbnail,
                                        onClick = {
                                            val playlistId = playlist.id ?: ""
                                            val title = android.net.Uri.encode(playlist.title ?: playlist.name ?: "")
                                            val art = android.net.Uri.encode(playlist.artworkUrl ?: playlist.thumbnail ?: "")
                                            val author = android.net.Uri.encode(playlist.subtitle ?: playlist.author ?: "")
                                            onNavigateToPlaylist("playlist/$playlistId?title=$title&art=$art&author=$author")
                                        },
                                        modifier = Modifier.size(150.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // 6. Results Available: Dedicated View for SONGS
                selectedCategory == SearchCategory.SONGS -> {
                    items(searchResults, key = { "${it.id}_${it.provider.name}" }) { track ->
                        val isTrackPlaying = isPlaying && currentPlayingId == track.id
                        SearchMediaRow(
                            track = track,
                            isPlaying = isTrackPlaying,
                            onClick = { onTrackClick(track) },
                            onClickMore = { selectedTrackForMenu = track },
                            onLongClick = { selectedTrackForMenu = track }
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 66.dp)
                        )
                    }
                }

                // 7. Results Available: Dedicated View for VIDEOS
                selectedCategory == SearchCategory.VIDEOS -> {
                    itemsIndexed(
                        items = searchResults,
                        key = { _, it -> "${it.id}_${it.provider.name}" }
                    ) { index, track ->
                        VideoListRow(
                            title = track.title,
                            channelName = track.artist,
                            thumbnailUrl = track.artworkUrl,
                            durationMs = track.durationMs,
                            onClick = { onTrackClick(track) },
                            onMoreClick = null,
                            showDivider = index < searchResults.lastIndex
                        )
                    }
                }

                // 8. Results Available: Dedicated View for ALBUMS
                selectedCategory == SearchCategory.ALBUMS -> {
                    items(searchResults.chunked(2), key = { pair -> pair.joinToString { it.id } }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            for (albumTrack in row) {
                                CoverCard(
                                    title = albumTrack.title,
                                    subtitle = albumTrack.artist,
                                    artworkUrl = albumTrack.artworkUrl,
                                    onClick = {
                                        val albumId = albumTrack.id
                                        val title = android.net.Uri.encode(albumTrack.title)
                                        val art = android.net.Uri.encode(albumTrack.artworkUrl ?: "")
                                        val artist = android.net.Uri.encode(albumTrack.artist)
                                        val type = if (albumTrack.title.contains("EP", ignoreCase = true)) "ep" else "album"
                                        onNavigateToAlbum("album/$albumId?title=$title&art=$art&artist=$artist&type=$type")
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // 9. Results Available: Dedicated View for PLAYLISTS
                selectedCategory == SearchCategory.PLAYLISTS -> {
                    items(searchResults.chunked(2), key = { pair -> pair.joinToString { it.id } }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            for (playlistTrack in row) {
                                CoverCard(
                                    title = playlistTrack.title,
                                    subtitle = playlistTrack.artist,
                                    artworkUrl = playlistTrack.artworkUrl,
                                    onClick = {
                                        val playlistId = playlistTrack.id
                                        val title = android.net.Uri.encode(playlistTrack.title)
                                        val art = android.net.Uri.encode(playlistTrack.artworkUrl ?: "")
                                        val author = android.net.Uri.encode(playlistTrack.artist)
                                        onNavigateToPlaylist("playlist/$playlistId?title=$title&art=$art&author=$author")
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // 10. Results Available: Dedicated View for ARTISTS
                selectedCategory == SearchCategory.ARTISTS -> {
                    items(artistResults, key = { it.id }) { artist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .applePressEffect()
                                .clickable {
                                    val name = android.net.Uri.encode(artist.name)
                                    val subs = artist.subscribers?.let { android.net.Uri.encode(it) } ?: ""
                                    val art = artist.artworkUrl?.let { android.net.Uri.encode(it) } ?: ""
                                    val route = "artist/${artist.id}?name=$name&subs=$subs&art=$art"
                                    onNavigateToArtist(route)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
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
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = artist.name,
                                        tint = PureWhite.copy(alpha = 0.6f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subCount = artist.subscribers
                                Text(
                                    text = if (!subCount.isNullOrBlank()) subCount else "Artist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedGrey,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 66.dp)
                        )
                    }
                }
            }
        }
    }
}
