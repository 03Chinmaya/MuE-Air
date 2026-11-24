package com.mue.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.mue.app.navigation.MuENavHost
import com.mue.core.auth.AuthRepository
import com.mue.core.datastore.SettingsDataStore
import com.mue.core.design.LocalHighPerformanceMode
import com.mue.core.design.MuETheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handled notification permission result */ }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handled storage permission result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(storagePermission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            storagePermissionLauncher.launch(storagePermission)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val isGuest = settingsDataStore.isGuestFlow.first()
            val guestId = settingsDataStore.guestIdFlow.first()
            if (isGuest && guestId == null) {
                authRepository.ensureGuestSession()
            }
            com.mue.core.network.SyncWorker.enqueue(applicationContext)
            com.mue.core.network.SyncWorker.enqueuePeriodic(applicationContext)
        }
        setContent {
            val highPerformance by settingsDataStore.highPerformanceFlow.collectAsState(initial = true)
            val theme by settingsDataStore.themeFlow.collectAsState(initial = "Pure Black OLED")
            val systemDark = isSystemInDarkTheme()
            val isDark = when (theme) {
                "Light Mode", "Light" -> false
                "System Default", "System" -> systemDark
                else -> true
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                     else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                         else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                )
                onDispose {}
            }

            CompositionLocalProvider(LocalHighPerformanceMode provides highPerformance) {
                MuETheme(darkTheme = isDark, theme = theme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MuENavHost()
                    }
                }
            }
        }
    }
}
