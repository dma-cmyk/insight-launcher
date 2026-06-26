package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.animation.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.SettingsManager
import com.example.ui.AppLauncherViewModel
import com.example.ui.AppLauncherViewModelFactory
import com.example.ui.components.SpaceBackground
import com.example.ui.screens.LauncherHomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.AiAssistantScreen
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

                    // Smooth sliding content transition
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (initialState == "home" && targetState == "ai_assistant") {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { -it } + fadeOut())
                            } else if (initialState == "ai_assistant" && targetState == "home") {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { it } + fadeOut())
                            } else if (initialState == "home" && targetState == "settings") {
                                (slideInVertically { it } + fadeIn()) togetherWith
                                        (slideOutVertically { -it } + fadeOut())
                            } else if (initialState == "settings" && targetState == "home") {
                                (slideInVertically { -it } + fadeIn()) togetherWith
                                        (slideOutVertically { it } + fadeOut())
                            } else {
                                fadeIn() togetherWith fadeOut()
                            }
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            "home" -> LauncherHomeScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { 
                                    currentScreen = "settings"
                                },
                                onNavigateToAiAssistant = {
                                    currentScreen = "ai_assistant"
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
                            "ai_assistant" -> AiAssistantScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = "home" }
                            )
                        }
                    }
                }
            }
        }
    }
}

