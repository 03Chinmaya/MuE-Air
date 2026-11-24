package com.mue.core.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Curated Apple-styled icons for MuE Apple Music-inspired UI.
 * Backed by Material Rounded icon primitives to ensure 100% binary stability.
 */
object SfIcons {
    val Home: ImageVector get() = Icons.Rounded.Home
    val Search: ImageVector get() = Icons.Rounded.Search
    val Library: ImageVector get() = Icons.Rounded.LibraryMusic
    val ListenTogether: ImageVector get() = Icons.Rounded.Headphones
    val Chat: ImageVector get() = Icons.Rounded.ChatBubbleOutline
    val Person: ImageVector get() = Icons.Rounded.AccountCircle
    val Notifications: ImageVector get() = Icons.Rounded.Notifications
    val Settings: ImageVector get() = Icons.Rounded.Settings
    val ArrowBack: ImageVector get() = Icons.Rounded.ArrowBackIosNew
    val More: ImageVector get() = Icons.Rounded.MoreHoriz
    val MoreVert: ImageVector get() = Icons.Rounded.MoreHoriz
    val Play: ImageVector get() = Icons.Rounded.PlayArrow
    val Pause: ImageVector get() = Icons.Rounded.Pause
    val Favorite: ImageVector get() = Icons.Rounded.Favorite
    val Download: ImageVector get() = Icons.Rounded.Download
    val Playlists: ImageVector get() = Icons.AutoMirrored.Rounded.QueueMusic
    val History: ImageVector get() = Icons.Rounded.Replay
    val MusicNote: ImageVector get() = Icons.Rounded.LibraryMusic
    val Shuffle: ImageVector get() = Icons.Rounded.Shuffle
    val Repeat: ImageVector get() = Icons.Rounded.Repeat
    val SkipPrevious: ImageVector get() = Icons.Rounded.SkipPrevious
    val SkipNext: ImageVector get() = Icons.Rounded.SkipNext
    val GridView: ImageVector get() = Icons.Rounded.GridView
    val ListView: ImageVector get() = Icons.AutoMirrored.Rounded.ViewList
    val Sort: ImageVector get() = Icons.AutoMirrored.Rounded.Sort
    val Filter: ImageVector get() = Icons.Rounded.FilterList
    val Close: ImageVector get() = Icons.Rounded.Close
}
