package com.mue.feature.library

import androidx.activity.compose.BackHandler
import com.mue.core.design.LayoutTokens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mue.core.design.CategoryCard
import com.mue.core.design.GlassDialog
import com.mue.core.design.HeroCard
import com.mue.core.design.ListRow
import com.mue.core.design.LocalHazeState
import com.mue.core.design.MutedGrey
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SfIcons
import com.mue.core.design.SystemAccentBlue
import com.mue.core.design.applePressEffect
import com.mue.core.design.liquidGlass
import com.mue.core.design.settingsRadioButtonColors
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    hazeState: HazeState? = LocalHazeState.current,
    onOpenProfile: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val likedCount by viewModel.likedCount.collectAsStateWithLifecycle()
    val downloadsCount by viewModel.downloadsCount.collectAsStateWithLifecycle()
    val playlistsCount by viewModel.playlistsCount.collectAsStateWithLifecycle()
    val historyCount by viewModel.historyCount.collectAsStateWithLifecycle()
    val unifiedPlaylists by viewModel.unifiedPlaylists.collectAsStateWithLifecycle()

    val likedArtworks by viewModel.likedArtworks.collectAsStateWithLifecycle()
    val historyArtworks by viewModel.historyArtworks.collectAsStateWithLifecycle()
    val playlistArtworks by viewModel.playlistArtworks.collectAsStateWithLifecycle()

    val librarySortOrder by viewModel.librarySortOrder.collectAsStateWithLifecycle()
    val libraryViewMode by viewModel.libraryViewMode.collectAsStateWithLifecycle()

    var showSortFilterSheet by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }

    val sortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Quick Filter Bar: Chips & View/Filter Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filterOptions = listOf("All", "Liked", "Recent", "Downloaded")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(filterOptions.size) { index ->
                    val filter = filterOptions[index]
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) MaterialTheme.colorScheme.background
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Quick Filter Buttons: View Mode Toggle & Sort/Filter Sheet
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val nextMode = if (libraryViewMode == "grid") "list" else "grid"
                        viewModel.setLibraryViewMode(nextMode)
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (libraryViewMode == "grid") SfIcons.ListView else SfIcons.GridView,
                        contentDescription = "Toggle View Mode",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showSortFilterSheet = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = SfIcons.Filter,
                        contentDescription = "Sort & Filter",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Main Library Scrolling Content
        if (libraryViewMode == "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LayoutTokens.ListBottomPaddingWithMiniPlayer),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header item: Hero Quick-Access Row
                    item(key = "bento_hero_row", span = { GridItemSpan(2) }) {
                        Column {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (selectedFilter == "All" || selectedFilter == "Liked") {
                                    item(key = "hero_liked") {
                                        HeroCard(
                                            title = "Liked Songs",
                                            count = "$likedCount songs",
                                            icon = SfIcons.Favorite,
                                            artworkPreviews = likedArtworks,
                                            onClick = { onNavigate("library/liked") },
                                            modifier = Modifier.width(if (selectedFilter == "Liked") 220.dp else 155.dp),
                                            hazeState = hazeState
                                        )
                                    }
                                }
                                if (selectedFilter == "All" || selectedFilter == "Downloaded") {
                                    item(key = "hero_downloads") {
                                        HeroCard(
                                            title = "Downloads",
                                            count = "$downloadsCount tracks",
                                            icon = SfIcons.Download,
                                            artworkPreviews = emptyList(),
                                            onClick = { onNavigate("library/downloads") },
                                            modifier = Modifier.width(if (selectedFilter == "Downloaded") 220.dp else 155.dp),
                                            hazeState = hazeState
                                        )
                                    }
                                }
                                if (selectedFilter == "All" || selectedFilter == "Recent") {
                                    item(key = "hero_history") {
                                        HeroCard(
                                            title = "Recently Played",
                                            count = "$historyCount tracks",
                                            icon = SfIcons.History,
                                            artworkPreviews = historyArtworks,
                                            onClick = { onNavigate("library/history") },
                                            modifier = Modifier.width(if (selectedFilter == "Recent") 220.dp else 155.dp),
                                            hazeState = hazeState
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "COLLECTIONS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Black
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }

                    // 2-Column Bento Category Grid items
                    item(key = "bento_playlists") {
                        CategoryCard(
                            title = "Playlists",
                            count = "$playlistsCount mixes",
                            icon = SfIcons.Playlists,
                            artworkPreviews = playlistArtworks,
                            onClick = { onNavigate("library/playlists") },
                            hazeState = hazeState
                        )
                    }

                    if (selectedFilter == "All") {
                        item(key = "bento_albums") {
                            CategoryCard(
                                title = "Albums",
                                count = "Saved albums",
                                icon = SfIcons.MusicNote,
                                onClick = { /* TODO */ },
                                hazeState = hazeState
                            )
                        }

                        item(key = "bento_artists") {
                            CategoryCard(
                                title = "Artists",
                                count = "Followed artists",
                                icon = SfIcons.Person,
                                onClick = { /* TODO */ },
                                hazeState = hazeState
                            )
                        }
                    }

                    if (selectedFilter in listOf("All", "Liked")) {
                        item(key = "bento_songs") {
                            CategoryCard(
                                title = "Songs",
                                count = "$likedCount tracks",
                                icon = SfIcons.MusicNote,
                                onClick = { onNavigate("library/liked") },
                                hazeState = hazeState
                            )
                        }
                    }

                    if (selectedFilter in listOf("All", "Recent")) {
                        // Wide 2-Column spanning tile
                        item(key = "bento_made_for_you", span = { GridItemSpan(2) }) {
                            CategoryCard(
                                title = "Made for You",
                                count = "Daily discovery & personal mixes",
                                icon = SfIcons.Shuffle,
                                isWide = true,
                                artworkPreviews = playlistArtworks,
                                onClick = { /* TODO */ },
                                hazeState = hazeState
                            )
                        }
                    }

                    if (selectedFilter in listOf("All", "Downloaded")) {
                        item(key = "bento_downloads") {
                            CategoryCard(
                                title = "Downloads",
                                count = "$downloadsCount offline",
                                icon = SfIcons.Download,
                                onClick = { onNavigate("library/downloads") },
                                hazeState = hazeState
                            )
                        }

                        item(key = "bento_local_files") {
                            CategoryCard(
                                title = "Local Files",
                                count = "Device audio",
                                icon = SfIcons.Library,
                                onClick = { onNavigate("library/downloads") },
                                hazeState = hazeState
                            )
                        }
                    }

                    if (selectedFilter in listOf("All", "Liked")) {
                        item(key = "bento_liked") {
                            CategoryCard(
                                title = "Liked",
                                count = "$likedCount tracks",
                                icon = SfIcons.Favorite,
                                onClick = { onNavigate("library/liked") },
                                hazeState = hazeState
                            )
                        }
                    }

                    if (selectedFilter in listOf("All", "Recent")) {
                        item(key = "bento_history") {
                            CategoryCard(
                                title = "History",
                                count = "$historyCount tracks",
                                icon = SfIcons.History,
                                onClick = { onNavigate("library/history") },
                                hazeState = hazeState
                            )
                        }
                    }
                }
            } else {
                // List View Mode
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LayoutTokens.ListBottomPaddingWithMiniPlayer),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "list_hero_row") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (selectedFilter == "All" || selectedFilter == "Liked") {
                                item(key = "hero_liked_list") {
                                    HeroCard(
                                        title = "Liked Songs",
                                        count = "$likedCount songs",
                                        icon = SfIcons.Favorite,
                                        artworkPreviews = likedArtworks,
                                        onClick = { onNavigate("library/liked") },
                                        modifier = Modifier.width(if (selectedFilter == "Liked") 220.dp else 155.dp),
                                        hazeState = hazeState
                                    )
                                }
                            }
                            if (selectedFilter == "All" || selectedFilter == "Downloaded") {
                                item(key = "hero_downloads_list") {
                                    HeroCard(
                                        title = "Downloads",
                                        count = "$downloadsCount tracks",
                                        icon = SfIcons.Download,
                                        artworkPreviews = emptyList(),
                                        onClick = { onNavigate("library/downloads") },
                                        modifier = Modifier.width(if (selectedFilter == "Downloaded") 220.dp else 155.dp),
                                        hazeState = hazeState
                                    )
                                }
                            }
                            if (selectedFilter == "All" || selectedFilter == "Recent") {
                                item(key = "hero_history_list") {
                                    HeroCard(
                                        title = "Recently Played",
                                        count = "$historyCount tracks",
                                        icon = SfIcons.History,
                                        artworkPreviews = historyArtworks,
                                        onClick = { onNavigate("library/history") },
                                        modifier = Modifier.width(if (selectedFilter == "Recent") 220.dp else 155.dp),
                                        hazeState = hazeState
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "COLLECTIONS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Black
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    item(key = "row_playlists") {
                        ListRow(
                            title = "Playlists",
                            subtitle = "$playlistsCount personal & imported mixes",
                            leadingIcon = SfIcons.Playlists,
                            onClick = { onNavigate("library/playlists") }
                        )
                    }

                    if (selectedFilter == "All") {
                        item(key = "row_albums") {
                            ListRow(
                                title = "Albums",
                                subtitle = "Saved albums & EPs",
                                leadingIcon = SfIcons.MusicNote,
                                onClick = { /* TODO */ }
                            )
                        }
                        item(key = "row_artists") {
                            ListRow(
                                title = "Artists",
                                subtitle = "Followed artists",
                                leadingIcon = SfIcons.Person,
                                onClick = { /* TODO */ }
                            )
                        }
                    }

                    if (selectedFilter in listOf("All", "Liked")) {
                        item(key = "row_songs") {
                            ListRow(
                                title = "Songs",
                                subtitle = "$likedCount tracks",
                                leadingIcon = SfIcons.MusicNote,
                                onClick = { onNavigate("library/liked") }
                            )
                        }
                    }

                    if (selectedFilter in listOf("All", "Recent")) {
                        item(key = "row_made_for_you") {
                            ListRow(
                                title = "Made for You",
                                subtitle = "Personal mixes and discovery",
                                leadingIcon = SfIcons.Shuffle,
                                onClick = { /* TODO */ }
                            )
                        }
                    }

                    if (selectedFilter in listOf("All", "Downloaded")) {
                        item(key = "row_downloads") {
                            ListRow(
                                title = "Downloads",
                                subtitle = "$downloadsCount offline tracks ready",
                                leadingIcon = SfIcons.Download,
                                onClick = { onNavigate("library/downloads") }
                            )
                        }
                        item(key = "row_local_files") {
                            ListRow(
                                title = "Local Files",
                                subtitle = "Tracks on device storage",
                                leadingIcon = SfIcons.Library,
                                onClick = { onNavigate("library/downloads") }
                            )
                        }
                    }

                    if (selectedFilter in listOf("All", "Liked")) {
                        item(key = "row_liked") {
                            ListRow(
                                title = "Liked",
                                subtitle = "$likedCount favorite songs",
                                leadingIcon = SfIcons.Favorite,
                                onClick = { onNavigate("library/liked") }
                            )
                        }
                    }

                    if (selectedFilter in listOf("All", "Recent")) {
                        item(key = "row_history") {
                            ListRow(
                                title = "History",
                                subtitle = "$historyCount recently played tracks",
                                leadingIcon = SfIcons.History,
                                onClick = { onNavigate("library/history") }
                            )
                        }
                    }
                }
            }
        }

    // Sort & Filter Bottom Sheet
    if (showSortFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortFilterSheet = false },
            sheetState = sortSheetState,
            containerColor = Color.Transparent,
            contentColor = PureWhite,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MutedGrey.copy(alpha = 0.4f))
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        hazeState = LocalHazeState.current,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Sort & Filter",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = PureWhite
                    )
                    Text(
                        text = "Customize your library display and ordering.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    // View Mode Selector
                    Text(
                        text = "VIEW MODE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MutedGrey,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("grid" to "Grid View", "list" to "List View").forEach { (mode, label) ->
                            val isModeSelected = libraryViewMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isModeSelected) PureWhite else PureWhite.copy(alpha = 0.08f))
                                    .clickable { viewModel.setLibraryViewMode(mode) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (mode == "grid") SfIcons.GridView else SfIcons.ListView,
                                        contentDescription = null,
                                        tint = if (isModeSelected) PureBlack else PureWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = label,
                                        color = if (isModeSelected) PureBlack else PureWhite,
                                        fontWeight = if (isModeSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Filter Options
                    Text(
                        text = "FILTER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MutedGrey,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Liked", "Recent", "Downloaded").forEach { opt ->
                            val isOptSelected = selectedFilter == opt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isOptSelected) PureWhite else PureWhite.copy(alpha = 0.08f))
                                    .clickable { selectedFilter = opt }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt,
                                    color = if (isOptSelected) PureBlack else PureWhite,
                                    fontWeight = if (isOptSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Sort By
                    Text(
                        text = "SORT BY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MutedGrey,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val sortOptions = listOf("Recently Added", "A-Z", "Most Played", "Recently Played")
                    sortOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setLibrarySortOrder(option)
                                    showSortFilterSheet = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = librarySortOrder == option,
                                onClick = {
                                    viewModel.setLibrarySortOrder(option)
                                    showSortFilterSheet = false
                                },
                                colors = settingsRadioButtonColors()
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                color = PureWhite,
                                fontWeight = if (librarySortOrder == option) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}
