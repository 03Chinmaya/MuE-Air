package com.mue.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mue.core.design.LocalHazeState
import com.mue.core.design.LocalNavAnimatedVisibilityScope
import com.mue.core.design.LocalSharedTransitionScope
import com.mue.core.design.PureBlack
import com.mue.feature.artist.ArtistProfileScreen
import com.mue.feature.artist.ArtistSongsScreen
import com.mue.feature.artist.ArtistVideosScreen
import com.mue.feature.auth.AuthChoiceScreen
import com.mue.feature.home.HomeScreen
import com.mue.feature.library.CollectionDetailScreen
import com.mue.feature.library.DownloadsScreen
import com.mue.feature.library.HistoryScreen
import com.mue.feature.library.LibraryScreen
import com.mue.feature.library.LibraryViewModel
import com.mue.feature.library.LikedSongsScreen
import com.mue.feature.library.PlaylistListScreen
import com.mue.feature.listen.ListenTogetherScreen
import com.mue.feature.listen.ListenTogetherSettingsScreen
import com.mue.feature.listen.ListenTogetherViewModel
import com.mue.feature.listen.UiState
import com.mue.feature.player.FullPlayerScreen
import com.mue.feature.player.LyricsViewModel
import com.mue.feature.player.MiniPlayer
import com.mue.feature.player.PlayerViewModel
import com.mue.feature.search.SearchScreen
import com.mue.feature.settings.AboutSettingsScreen
import com.mue.feature.settings.AppearanceSettingsScreen
import com.mue.feature.settings.DangerZoneScreen
import com.mue.feature.settings.GlobalSettingsScreen
import com.mue.feature.settings.LinkedDevicesScreen
import com.mue.feature.settings.NotificationsSettingsScreen
import com.mue.feature.settings.PlaybackSettingsScreen
import com.mue.feature.settings.PrivacySettingsScreen
import com.mue.feature.settings.ProfileScreen
import com.mue.feature.settings.StorageSettingsScreen

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MuENavHost(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val playbackState by viewModel.state.collectAsState()
    val currentQueue by viewModel.queue.collectAsState()
    val lyricsViewModel: LyricsViewModel = hiltViewModel()
    val currentLyricLine by lyricsViewModel.currentLine.collectAsState()
    val audioRoute by viewModel.audioRoute.collectAsState()
    val ltViewModel: ListenTogetherViewModel = hiltViewModel()
    val ltUiState by ltViewModel.uiState.collectAsStateWithLifecycle()
    val isLtImmersive = (currentRoute == "listen" || currentRoute == "listen_together") &&
        (ltUiState is UiState.Connecting || ltUiState is UiState.InRoom)
    var showFullPlayer by remember { mutableStateOf(false) }

    val rootTabs = setOf("home", "listen", "listen_together", "search", "library")
    BackHandler(enabled = navController.previousBackStackEntry != null || (currentRoute != null && currentRoute != "home")) {
        if (currentRoute in rootTabs && currentRoute != "home") {
            navController.navigate("home") {
                popUpTo("home") { inclusive = true }
            }
        } else {
            navController.popBackStack()
        }
    }

    val showMiniPlayer = currentRoute?.startsWith("settings") != true &&
        currentRoute != "listen_settings" &&
        currentRoute != "profile" &&
        playbackState.currentTrack != null

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            Box(modifier = Modifier.fillMaxSize()) {
                MuELayout(
                    navController = navController,
                    isImmersive = isLtImmersive,
                    miniPlayer = { layoutHazeState ->
                        AnimatedVisibility(
                            visible = showMiniPlayer && !showFullPlayer,
                            enter = fadeIn(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(200))
                        ) {
                            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@AnimatedVisibility) {
                                playbackState.currentTrack?.let { track ->
                                    MiniPlayer(
                                        trackId = track.id,
                                        title = track.title,
                                        artist = track.artist,
                                        artworkUrl = track.artworkUrl,
                                        isPlaying = playbackState.isPlaying,
                                        positionMs = playbackState.positionMs,
                                        durationMs = playbackState.durationMs,
                                        onPlayPause = { viewModel.togglePlayPause() },
                                        onNext = { viewModel.skipToNext() },
                                        onClick = { showFullPlayer = true },
                                        youtubeVideoId = track.youtubeVideoId,
                                        track = track,
                                        onShare = { trackToShare -> viewModel.shareTrack(trackToShare, context) },
                                        hazeState = layoutHazeState
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            enterTransition = { fadeIn(animationSpec = tween(150)) },
                            exitTransition = { fadeOut(animationSpec = tween(150)) },
                            popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                            popExitTransition = { fadeOut(animationSpec = tween(150)) }
                        ) {
                            composable("home") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    HomeScreen(
                                        onTrackClick = { track, queue, source ->
                                            viewModel.playQueue(queue.ifEmpty { listOf(track) }, queue.indexOf(track).coerceAtLeast(0), source)
                                            showFullPlayer = true
                                        },
                                        onPlaylistClick = { playlistTrack ->
                                            val title = android.net.Uri.encode(playlistTrack.title)
                                            val art = android.net.Uri.encode(playlistTrack.artworkUrl ?: "")
                                            val author = android.net.Uri.encode(playlistTrack.artist)
                                            navController.navigate("playlist/${playlistTrack.id}?title=$title&art=$art&author=$author&isImported=true")
                                        },
                                        onNavigateToArtist = { routeOrId ->
                                            if (routeOrId.startsWith("artist/")) {
                                                navController.navigate(routeOrId)
                                            } else {
                                                navController.navigate("artist/$routeOrId")
                                            }
                                        }
                                    )
                                }
                            }
                            composable("search") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    SearchScreen(
                                        onBack = {},
                                        onNavigateToLibrary = {
                                            navController.navigate("library") {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onNavigateToArtist = { routeOrId ->
                                            if (routeOrId.startsWith("artist/")) {
                                                navController.navigate(routeOrId)
                                            } else {
                                                navController.navigate("artist/$routeOrId")
                                            }
                                        },
                                        onNavigateToAlbum = { albumRoute ->
                                            navController.navigate(albumRoute)
                                        },
                                        onNavigateToPlaylist = { playlistRoute ->
                                            navController.navigate(playlistRoute)
                                        }
                                    )
                                }
                            }
                            composable("listen") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    ListenTogetherScreen(
                                        onOpenSettings = { navController.navigate("listen_settings") },
                                        onNavigateToAuth = { navController.navigate("auth") },
                                        viewModel = ltViewModel
                                    )
                                }
                            }
                            composable("listen_together") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    ListenTogetherScreen(
                                        onOpenSettings = { navController.navigate("listen_settings") },
                                        onNavigateToAuth = { navController.navigate("auth") },
                                        viewModel = ltViewModel
                                    )
                                }
                            }
                            composable("listen_settings") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    ListenTogetherSettingsScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("library") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    LibraryScreen(
                                        hazeState = LocalHazeState.current,
                                        onOpenProfile = { navController.navigate("profile") },
                                        onNavigate = { route -> navController.navigate(route) }
                                    )
                                }
                            }
                            composable("library/liked") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    LikedSongsScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("library/downloads") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    DownloadsScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("library/history") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    HistoryScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("library/playlists") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    val libVm: LibraryViewModel = hiltViewModel()
                                    val playlists by libVm.unifiedPlaylists.collectAsState()
                                    PlaylistListScreen(
                                        playlists = playlists,
                                        onBack = { navController.popBackStack() },
                                        onPlay = { id, isImported -> libVm.playPlaylist(id, isImported) },
                                        onOpenPlaylist = { playlist ->
                                            val title = android.net.Uri.encode(playlist.name)
                                            val art = android.net.Uri.encode(playlist.artworkUrl ?: "")
                                            val isImp = playlist.isImported
                                            navController.navigate("playlist/${playlist.id}?title=$title&art=$art&isImported=$isImp")
                                        },
                                        onCreatePlaylist = { name -> libVm.createPlaylist(name) }
                                    )
                                }
                            }
                            composable(
                                route = "artist/{artistId}?name={name}&subs={subs}&art={art}",
                                arguments = listOf(
                                    navArgument("artistId") { type = NavType.StringType },
                                    navArgument("name") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("subs") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("art") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { backStackEntry ->
                                val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    ArtistProfileScreen(
                                        onBack = { navController.popBackStack() },
                                        onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") },
                                        onNavigateToAllSongs = { navController.navigate("artist/$artistId/songs") },
                                        onNavigateToAllVideos = { navController.navigate("artist/$artistId/videos") }
                                    )
                                }
                            }
                            composable(
                                route = "artist/{artistId}/songs",
                                arguments = listOf(
                                    navArgument("artistId") { type = NavType.StringType }
                                )
                            ) {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    ArtistSongsScreen(
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            composable(
                                route = "artist/{artistId}/videos",
                                arguments = listOf(
                                    navArgument("artistId") { type = NavType.StringType }
                                )
                            ) {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    ArtistVideosScreen(
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            composable(
                                route = "collection/{type}/{id}?title={title}&art={art}&sub={sub}&isImported={isImported}",
                                arguments = listOf(
                                    navArgument("type") { type = NavType.StringType },
                                    navArgument("id") { type = NavType.StringType },
                                    navArgument("title") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("art") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("sub") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("isImported") {
                                        type = NavType.BoolType
                                        defaultValue = false
                                    }
                                )
                            ) {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    CollectionDetailScreen(
                                        onBack = { navController.popBackStack() },
                                        onNavigateToArtist = { artistRouteOrId ->
                                            if (artistRouteOrId.startsWith("artist/")) {
                                                navController.navigate(artistRouteOrId)
                                            } else {
                                                navController.navigate("artist/$artistRouteOrId")
                                            }
                                        },
                                        onTrackClick = { track, queue, source ->
                                            viewModel.playQueue(queue, queue.indexOf(track).coerceAtLeast(0), source)
                                            showFullPlayer = true
                                        }
                                    )
                                }
                            }
                            composable(
                                route = "album/{albumId}?title={title}&art={art}&artist={artist}&type={type}",
                                arguments = listOf(
                                    navArgument("albumId") { type = NavType.StringType },
                                    navArgument("title") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("art") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("artist") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("type") {
                                        type = NavType.StringType
                                        defaultValue = "album"
                                    }
                                )
                            ) {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    CollectionDetailScreen(
                                        onBack = { navController.popBackStack() },
                                        onNavigateToArtist = { artistRouteOrId ->
                                            if (artistRouteOrId.startsWith("artist/")) {
                                                navController.navigate(artistRouteOrId)
                                            } else {
                                                navController.navigate("artist/$artistRouteOrId")
                                            }
                                        },
                                        onTrackClick = { track, queue, source ->
                                            viewModel.playQueue(queue, queue.indexOf(track).coerceAtLeast(0), source)
                                            showFullPlayer = true
                                        }
                                    )
                                }
                            }
                            composable(
                                route = "playlist/{playlistId}?title={title}&art={art}&author={author}&isImported={isImported}",
                                arguments = listOf(
                                    navArgument("playlistId") { type = NavType.StringType },
                                    navArgument("title") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("art") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("author") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("isImported") {
                                        type = NavType.BoolType
                                        defaultValue = false
                                    }
                                )
                            ) {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    CollectionDetailScreen(
                                        onBack = { navController.popBackStack() },
                                        onNavigateToArtist = { artistRouteOrId ->
                                            if (artistRouteOrId.startsWith("artist/")) {
                                                navController.navigate(artistRouteOrId)
                                            } else {
                                                navController.navigate("artist/$artistRouteOrId")
                                            }
                                        },
                                        onTrackClick = { track, queue, source ->
                                            viewModel.playQueue(queue, queue.indexOf(track).coerceAtLeast(0), source)
                                            showFullPlayer = true
                                        }
                                    )
                                }
                            }
                            composable("profile") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    ProfileScreen(
                                        onNavigate = { route -> navController.navigate(route) },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            composable("settings") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    GlobalSettingsScreen(
                                        onNavigate = { route -> navController.navigate(route) },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            composable("settings/root") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    GlobalSettingsScreen(
                                        onNavigate = { route -> navController.navigate(route) },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            composable("settings/playback") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    PlaybackSettingsScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("settings/storage") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    StorageSettingsScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("settings/privacy") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    PrivacySettingsScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("settings/notifications") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    NotificationsSettingsScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("settings/devices") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    LinkedDevicesScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("settings/appearance") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    AppearanceSettingsScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("settings/about") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    AboutSettingsScreen(onBack = { navController.popBackStack() })
                                }
                            }
                            composable("settings/danger") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    DangerZoneScreen(
                                        onNavigate = { route -> navController.navigate(route) },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            composable("auth") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    AuthChoiceScreen(onNavigateToHome = { navController.popBackStack() })
                                }
                            }
                        }
                    }
                }

                // Full Player Modal with vertical spring animation
                AnimatedVisibility(
                    visible = showFullPlayer,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                    )
                ) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@AnimatedVisibility) {
                        val playbackPlayingFrom by viewModel.playingFrom.collectAsState()
                        val userPlaylists by viewModel.userPlaylists.collectAsState()
                        val isLiked by viewModel.isLiked.collectAsState()
                        playbackState.currentTrack?.let { track ->
                            FullPlayerScreen(
                                trackId = track.id,
                                title = track.title,
                                artist = track.artist,
                                artworkUrl = track.artworkUrl,
                                animatedArtworkUrl = track.animatedArtworkUrl,
                                isPlaying = playbackState.isPlaying,
                                positionMs = playbackState.positionMs,
                                durationMs = playbackState.durationMs,
                                queue = currentQueue,
                                lyricsRepository = viewModel.lyricsRepository,
                                currentLyricLine = currentLyricLine,
                                onSeekTo = { viewModel.seekTo(it) },
                                onPlayPause = { viewModel.togglePlayPause() },
                                onPrevious = { viewModel.skipToPrevious() },
                                onNext = { viewModel.skipToNext() },
                                onShuffle = { viewModel.toggleShuffle() },
                                onRepeat = { viewModel.toggleRepeat() },
                                isShuffleEnabled = playbackState.isShuffleEnabled,
                                isRepeatEnabled = playbackState.isRepeatEnabled,
                                repeatMode = playbackState.repeatMode,
                                onTrackSelect = { viewModel.playTrack(it) },
                                onRemoveFromQueue = { viewModel.removeFromQueue(it.id) },
                                onListenTogether = {
                                    showFullPlayer = false
                                    navController.navigate("listen") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                    }
                                },
                                audioRoute = audioRoute,
                                track = track,
                                youtubeVideoId = track.youtubeVideoId,
                                onShare = { trackToShare -> viewModel.shareTrack(trackToShare, context) },
                                playingFrom = playbackPlayingFrom,
                                onGoToArtist = { artistName ->
                                    showFullPlayer = false
                                    navController.navigate("artist/${android.net.Uri.encode(artistName)}")
                                },
                                onSaveToLibrary = { trackToSave -> viewModel.saveToLibrary(trackToSave) },
                                isLiked = isLiked,
                                onToggleLike = { trackToLike -> viewModel.toggleLike(trackToLike) },
                                playlists = userPlaylists,
                                onAddToPlaylist = { trackToSave, playlistId -> viewModel.addTrackToPlaylist(trackToSave, playlistId) },
                                onCreatePlaylistAndAdd = { name, trackToSave -> viewModel.createPlaylistAndAddTrack(name, trackToSave) },
                                onSetSleepTimer = { minutes -> viewModel.setSleepTimer(minutes) },
                                onSetPlaybackSpeed = { speed -> viewModel.setPlaybackSpeed(speed) },
                                onSelectAudioDevice = { device -> viewModel.setPreferredAudioDevice(device) },
                                onDismiss = { showFullPlayer = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
