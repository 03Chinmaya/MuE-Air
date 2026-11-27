package com.mue.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.mue.core.design.LocalTabReselectFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mue.app.R
import com.mue.core.design.BottomBarTab
import com.mue.core.design.FloatingBottomBar
import com.mue.core.design.FloatingHeaderPill
import com.mue.core.design.LocalHazeState
import com.mue.core.design.MuEHeaderLogo
import com.mue.core.design.PureBlack
import com.mue.core.design.PureWhite
import com.mue.core.design.SfIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun MuELayout(
    navController: NavHostController,
    miniPlayer: @Composable ((HazeState) -> Unit)? = null,
    isImmersive: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val hazeState = remember { HazeState() }
    val reselectFlow = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }
    val coroutineScope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tabs = remember {
        listOf(
            BottomBarTab(route = "home",    icon = SfIcons.Home,                                   label = "Home"),
            BottomBarTab(route = "listen",  iconResId = com.mue.core.design.R.drawable.l_t,        label = "Listen"),
            BottomBarTab(route = "search",  icon = SfIcons.Search,                                 label = "Search"),
            BottomBarTab(route = "library", iconResId = com.mue.core.design.R.drawable.library,    label = "Library"),
        )
    }

    val selectedTabIndex = remember(currentRoute) {
        val mappedRoute = if (currentRoute == "listen_together") "listen" else currentRoute
        val idx = tabs.indexOfFirst { it.route == mappedRoute }
        if (idx >= 0) idx else 0
    }

    val isRootTab = currentRoute in setOf(null, "home", "listen", "listen_together", "search", "library")

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalTabReselectFlow provides reselectFlow.asSharedFlow()
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (isRootTab && !isImmersive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val titleText = when (currentRoute) {
                                "search" -> "Search"
                                "listen", "listen_together" -> "Listen Together"
                                "library" -> "Library"
                                else -> ""
                            }
                            if (titleText.isEmpty()) {
                                MuEHeaderLogo(
                                    height = 34.dp
                                )
                            } else {
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 24.sp,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            FloatingHeaderPill(
                                onSettingsClick = {
                                    if (currentRoute == "listen" || currentRoute == "listen_together") {
                                        navController.navigate("listen_settings")
                                    } else {
                                        navController.navigate("settings/root")
                                    }
                                },
                                onProfileClick = { navController.navigate("profile") },
                                contentDescriptionSettings = if (currentRoute == "listen" || currentRoute == "listen_together") "Listen Settings" else "Settings"
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (!isImmersive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                    ) {
                        miniPlayer?.invoke(hazeState)
                        if (isRootTab) {
                            FloatingBottomBar(
                                tabs = tabs,
                                selectedIndex = selectedTabIndex,
                                onTabSelected = { index ->
                                    val target = tabs[index].route
                                    val currentMapped = if (currentRoute == "listen_together") "listen" else currentRoute
                                    if (target != currentMapped) {
                                        navController.navigate(target) {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                onTabReselected = { index ->
                                    val target = tabs[index].route
                                    coroutineScope.launch { reselectFlow.emit(target) }
                                },
                                hazeState = hazeState
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureBlack)
                    .hazeSource(state = hazeState)
            ) {
                content(padding)
            }
        }
    }
}
