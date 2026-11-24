package com.mue.feature.home

import com.mue.core.design.LayoutTokens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mue.core.design.DarkGrey
import com.mue.core.design.HeroCard
import com.mue.core.design.MediaCard
import com.mue.core.design.MuEHeaderLogo
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SectionHeader
import com.mue.core.design.ShimmerBox
import com.mue.core.design.SkeletonLine
import com.mue.core.design.applePressEffect
import com.mue.core.design.thumbnailBorder
import com.mue.core.model.Track

private val HOME_TITLE_LIFT = 28.dp

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onTrackClick: (Track, List<Track>, String) -> Unit = { track, queue, source -> viewModel.playTrack(track, queue, source) },
    onPlaylistClick: (Track) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val sections by viewModel.dynamicSections.collectAsStateWithLifecycle()
    val followedArtists by viewModel.followedArtists.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(sections) {
        if (sections.isNotEmpty()) {
            val imageLoader = SingletonImageLoader.get(context)
            sections.forEach { section ->
                section.items.take(8).forEach { track ->
                    val url = track.artworkUrl
                    if (!url.isNullOrBlank()) {
                        imageLoader.enqueue(
                            ImageRequest.Builder(context)
                                .data(url)
                                .build()
                        )
                    }
                }
            }
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listState = rememberLazyListState()

    LaunchedEffect(error) {
        if (error != null) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = LayoutTokens.ListBottomPaddingWithMiniPlayer
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Loading Shimmer Skeletons
        if (isLoading && sections.isEmpty()) {
            item(key = "skeleton_hero") {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    SkeletonLine(
                        fraction = 0.35f,
                        height = 20.dp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(2) {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillParentMaxWidth(0.85f)
                                    .height(180.dp),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }

            items(2, key = { "skeleton_row_$it" }) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SkeletonLine(
                        fraction = 0.4f,
                        height = 18.dp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(4) {
                            Column(modifier = Modifier.width(140.dp)) {
                                ShimmerBox(
                                    modifier = Modifier.size(140.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                SkeletonLine(fraction = 0.85f, height = 12.dp)
                                Spacer(Modifier.height(4.dp))
                                SkeletonLine(fraction = 0.55f, height = 10.dp)
                            }
                        }
                    }
                }
            }
        }

        // Error State Banner with Retry
        if (error != null && !isLoading) {
            item(key = "home_error_banner") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = error ?: "Failed to load music",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2C2C2E))
                            .applePressEffect()
                            .clickable { viewModel.retry() }
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelLarge,
                            color = PureWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Followed Artists Carousel
        if (followedArtists.isNotEmpty()) {
            item(key = "followed_artists_header") {
                SectionHeader(title = "Artists You Follow")
            }
            item(key = "followed_artists_carousel") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    items(followedArtists, key = { it.artistId }) { artist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(76.dp)
                                .applePressEffect()
                                .clickable { onNavigateToArtist(artist.artistId) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(DarkGrey),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!artist.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(artist.thumbnailUrl)
                                            .size(144)
                                            .crossfade(true)
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
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Dynamic Sections Loop
        items(sections, key = { it.id }) { section ->
            when (section.type) {
                "QUICK_PICKS" -> QuickPicksSection(
                    tracks = section.items,
                    skeletonState = section.skeletonState,
                    onTrackClick = { track -> onTrackClick(track, section.items, "Quick Picks") }
                )
                "IMPORTED_PLAYLISTS" -> ImportedPlaylistsSection(
                    playlists = section.items,
                    skeletonState = section.skeletonState,
                    onPlaylistClick = { playlist -> onPlaylistClick(playlist) }
                )
                "CAROUSEL" -> StandardCarouselSection(
                    title = section.title,
                    tracks = section.items,
                    skeletonState = section.skeletonState,
                    onTrackClick = { track -> onTrackClick(track, section.items, section.title) }
                )
            }
        }
    }
}

/**
 * Quick Picks 3-column x 7-row responsive grid (up to 21 items).
 * Nested in Home LazyColumn with zero scroll conflict, strict zero-border policy,
 * and Apple press spring animations.
 */
@Composable
fun QuickPicksSection(
    tracks: List<Track>,
    skeletonState: SkeletonState = SkeletonState.Content(tracks),
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayTracks = when {
        tracks.isNotEmpty() -> tracks
        skeletonState is SkeletonState.Content && skeletonState.tracks.isNotEmpty() -> skeletonState.tracks
        else -> emptyList()
    }

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        SectionHeader(title = "Quick Picks")
        if (displayTracks.isNotEmpty()) {
            val columns = remember(displayTracks) { displayTracks.take(21).chunked(3) }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(columns, key = { col -> "col_${col.first().id}" }) { columnTracks ->
                    Column(
                        modifier = Modifier.fillParentMaxWidth(0.85f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        columnTracks.forEach { track ->
                            CompactTrackRow(
                                track = track,
                                onClick = { onTrackClick(track) }
                            )
                        }
                    }
                }
            }
        } else if (skeletonState is SkeletonState.Visible) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(2) {
                    Column(
                        modifier = Modifier.fillParentMaxWidth(0.85f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(3) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ShimmerBox(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    SkeletonLine(fraction = 0.7f, height = 14.dp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    SkeletonLine(fraction = 0.4f, height = 12.dp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "No tracks available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Imported Playlists horizontal carousel.
 */
@Composable
fun ImportedPlaylistsSection(
    playlists: List<Track>,
    skeletonState: SkeletonState = SkeletonState.Content(playlists),
    onPlaylistClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayPlaylists = when {
        playlists.isNotEmpty() -> playlists
        skeletonState is SkeletonState.Content && skeletonState.tracks.isNotEmpty() -> skeletonState.tracks
        else -> emptyList()
    }

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        SectionHeader(title = "Imported Playlists")
        if (displayPlaylists.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(displayPlaylists, key = { "imported_${it.id}" }) { playlist ->
                    MediaCard(
                        title = playlist.title,
                        subtitle = playlist.artist,
                        artworkUrl = playlist.artworkUrl,
                        onClick = { onPlaylistClick(playlist) },
                        sharedKey = "imported_${playlist.id}"
                    )
                }
            }
        } else if (skeletonState is SkeletonState.Visible) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(3) {
                    Column(modifier = Modifier.width(140.dp)) {
                        ShimmerBox(
                            modifier = Modifier.size(140.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        SkeletonLine(fraction = 0.85f, height = 12.dp)
                        Spacer(Modifier.height(4.dp))
                        SkeletonLine(fraction = 0.55f, height = 10.dp)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "No imported playlists yet. Paste a YouTube or Spotify link in Search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Standard horizontal carousel for music tracks/albums.
 * Renders HeroCard for "Featured For You" or MediaCard for others.
 */
@Composable
fun StandardCarouselSection(
    title: String,
    tracks: List<Track>,
    skeletonState: SkeletonState = SkeletonState.Content(tracks),
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHero = title == "Featured For You"
    val displayTracks = when {
        tracks.isNotEmpty() -> tracks
        skeletonState is SkeletonState.Content && skeletonState.tracks.isNotEmpty() -> skeletonState.tracks
        else -> emptyList()
    }

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        SectionHeader(title = title)
        if (displayTracks.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isHero) {
                    items(displayTracks.take(5), key = { "hero_${it.id}" }) { track ->
                        HeroCard(
                            title = track.title,
                            subtitle = track.artist,
                            artworkUrl = track.artworkUrl,
                            onClick = { onTrackClick(track) },
                            modifier = Modifier.fillParentMaxWidth(0.85f)
                        )
                    }
                } else {
                    items(displayTracks, key = { "${title}_${it.id}" }) { track ->
                        val badge = when {
                            track.type.equals("ep", ignoreCase = true) -> "EP"
                            track.type.equals("single", ignoreCase = true) -> "Single"
                            else -> null
                        }
                        MediaCard(
                            title = track.title,
                            subtitle = track.artist,
                            artworkUrl = track.artworkUrl,
                            onClick = { onTrackClick(track) },
                            sharedKey = "artwork_${title}_${track.id}",
                            badge = badge
                        )
                    }
                }
            }
        } else if (skeletonState is SkeletonState.Visible) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isHero) {
                    items(2) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillParentMaxWidth(0.85f)
                                .height(180.dp),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                } else {
                    items(4) {
                        Column(modifier = Modifier.width(140.dp)) {
                            ShimmerBox(
                                modifier = Modifier.size(140.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            SkeletonLine(fraction = 0.85f, height = 12.dp)
                            Spacer(Modifier.height(4.dp))
                            SkeletonLine(fraction = 0.55f, height = 10.dp)
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "No tracks available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * MuE Wordmark Title inside scrollable content matching Apple Music's in-page header.
 */
@Composable
fun HomeTitle(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MuEHeaderLogo(height = 32.dp)
    }
}

/**
 * One row of Quick Picks' 4-to-a-column pager matching BitChord's CompactTrackRow.
 */
@Composable
fun CompactTrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .applePressEffect()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val artShape = RoundedCornerShape(8.dp)
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(track.artworkUrl)
                .size(128)
                .crossfade(true)
                .build(),
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(artShape)
                .thumbnailBorder(artShape)
                .background(DarkGrey)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
