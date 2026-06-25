package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.SettingsManager
import com.example.ui.AppLauncherViewModel
import com.example.ui.AppLauncherViewModelFactory
import com.example.ui.components.SpaceBackground
import com.example.ui.screens.LauncherHomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite Room cache, SharedPreferences setting manager and repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AppRepository(database.appDao())
        val settingsManager = SettingsManager(applicationContext)

        setContent {
            MyApplicationTheme {
                val viewModel: AppLauncherViewModel = viewModel(
                    factory = AppLauncherViewModelFactory(repository, settingsManager, applicationContext)
                )

                val currentBgUrl by viewModel.currentBgUrl.collectAsState()
                var currentScreen by remember { mutableStateOf("home") }
                var bgScrollOffsetX by remember { mutableStateOf(0f) }
                var bgScrollOffsetY by remember { mutableStateOf(0f) }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Responsive space backdrop with dimming overlay layer and high density parallax swiping
                    SpaceBackground(
                        bgUrl = currentBgUrl,
                        scrollOffsetX = bgScrollOffsetX,
                        scrollOffsetY = bgScrollOffsetY
                    )

                    // Lightweight custom state router for extreme startup speed and low battery overhead
                    when (currentScreen) {
                        "home" -> LauncherHomeScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { 
                                currentScreen = "settings"
                            },
                            onScrollOffsetChanged = { x, y ->
                                bgScrollOffsetX = x
                                bgScrollOffsetY = y
                            }
                        )
                        "settings" -> SettingsScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "home" }
                        )
                    }
                }
            }
        }
    }
}

