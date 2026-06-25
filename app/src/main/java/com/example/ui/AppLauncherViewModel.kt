package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppInfo
import com.example.data.AppRepository
import com.example.data.InstalledApp
import com.example.data.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log

class AppLauncherViewModel(
    private val repository: AppRepository,
    val settingsManager: SettingsManager,
    private val context: Context
) : ViewModel() {

    private val TAG = "AppLauncherViewModel"
    private var isAnalysisCancelled = false

    private val _analysisProgressPercent = MutableStateFlow(0f)
    val analysisProgressPercent: StateFlow<Float> = _analysisProgressPercent.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisProgress = MutableStateFlow("")
    val analysisProgress: StateFlow<String> = _analysisProgress.asStateFlow()

    private val _selectedApp = MutableStateFlow<InstalledApp?>(null)
    val selectedApp: StateFlow<InstalledApp?> = _selectedApp.asStateFlow()

    // Expose background image URL reactively
    val currentBgUrl: StateFlow<String> = settingsManager.bgImageUrl
    val viewMode: StateFlow<String> = settingsManager.viewMode

    // Combine installed apps with search query
    val appListState: StateFlow<List<InstalledApp>> = repository.getInstalledAppsFlow(context)
        .combine(_searchQuery) { apps, query ->
            if (query.isBlank()) {
                apps
            } else {
                apps.filter {
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true) ||
                    (it.cachedInfo?.category?.contains(query, ignoreCase = true) ?: false) ||
                    (it.cachedInfo?.tags?.contains(query, ignoreCase = true) ?: false)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectApp(app: InstalledApp?) {
        _selectedApp.value = app
    }

    fun setViewMode(mode: String) {
        settingsManager.setViewMode(mode)
    }

    // Launch app on device
    fun launchApp(packageName: String) {
        val lang = settingsManager.getAiLanguage()
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, Localization.get("launch_error", lang, packageName), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, Localization.get("init_error", lang, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    // Single App Manual/On-demand analysis
    fun analyzeSingleApp(
        app: InstalledApp,
        userContextText: String? = null,
        fileName: String? = null,
        fileMimeType: String? = null,
        fileBytes: ByteArray? = null
    ) {
        if (_isAnalyzing.value) return
        val lang = settingsManager.getAiLanguage()
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisProgress.value = Localization.get("analyzing", lang, app.label, 1, 1)
            try {
                val success = repository.analyzeAndCacheApp(
                    context = context,
                    packageName = app.packageName,
                    label = app.label,
                    isSystemApp = app.isSystemApp,
                    settingsManager = settingsManager,
                    userContextText = userContextText,
                    fileName = fileName,
                    fileMimeType = fileMimeType,
                    fileBytes = fileBytes
                )
                if (success) {
                    Toast.makeText(context, Localization.get("analysis_success", lang, app.label), Toast.LENGTH_SHORT).show()
                    // Update currently selected app if it is the one being analyzed
                    if (_selectedApp.value?.packageName == app.packageName) {
                        // Let Room flow update the app list, then re-select it
                        val updatedList = appListState.value
                        val updatedApp = updatedList.find { it.packageName == app.packageName }
                        _selectedApp.value = updatedApp
                    }
                } else {
                    Toast.makeText(context, Localization.get("analysis_failed", lang, app.label), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in manual analysis", e)
                Toast.makeText(context, Localization.get("general_error", lang, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            } finally {
                _isAnalyzing.value = false
                _analysisProgress.value = ""
            }
        }
    }

    // Sequential auto-analysis of all unanalyzed apps with rate limit resilience and retries
    fun autoAnalyzeAllUnanalyzed() {
        if (_isAnalyzing.value) return
        val lang = settingsManager.getAiLanguage()
        viewModelScope.launch {
            val unanalyzed = appListState.value.filter { !it.isAnalyzed }
            if (unanalyzed.isEmpty()) {
                Toast.makeText(context, Localization.get("all_analyzed", lang), Toast.LENGTH_SHORT).show()
                return@launch
            }

            _isAnalyzing.value = true
            isAnalysisCancelled = false
            _analysisProgressPercent.value = 0f
            val total = unanalyzed.size
            var successCount = 0

            for ((index, app) in unanalyzed.withIndex()) {
                if (isAnalysisCancelled) break
                
                _analysisProgressPercent.value = index.toFloat() / total
                var success = false
                var attempts = 0
                val maxAttempts = 3

                while (attempts < maxAttempts && !success) {
                    if (isAnalysisCancelled) break
                    attempts++
                    
                    if (attempts > 1) {
                        _analysisProgress.value = Localization.get("analyzing_retry", lang, app.label, index + 1, total, attempts, maxAttempts)
                    } else {
                        _analysisProgress.value = Localization.get("analyzing", lang, app.label, index + 1, total)
                    }
                    
                    success = repository.analyzeAndCacheApp(
                        context = context,
                        packageName = app.packageName,
                        label = app.label,
                        isSystemApp = app.isSystemApp,
                        settingsManager = settingsManager
                    )

                    if (success) {
                        successCount++
                    } else if (attempts < maxAttempts && !isAnalysisCancelled) {
                        // Error fallback: Wait 15s to bypass rate limit
                        for (sec in 15 downTo 1) {
                            if (isAnalysisCancelled) break
                            _analysisProgress.value = Localization.get("temp_error", lang, sec, app.label)
                            delay(1000)
                        }
                    }
                }

                if (isAnalysisCancelled) break

                // Safe interval to avoid 15 RPM rate limit of free tier
                if (index < total - 1) {
                    for (sec in 4 downTo 1) {
                        if (isAnalysisCancelled) break
                        _analysisProgress.value = Localization.get("waiting_next", lang, sec)
                        delay(1000)
                    }
                }
            }

            _isAnalyzing.value = false
            _analysisProgressPercent.value = 1.0f
            _analysisProgress.value = ""

            if (isAnalysisCancelled) {
                Toast.makeText(context, Localization.get("batch_cancelled", lang, successCount), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, Localization.get("batch_complete", lang, successCount), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Cancel active background batch analysis
    fun cancelAutoAnalysis() {
        val lang = settingsManager.getAiLanguage()
        isAnalysisCancelled = true
        _analysisProgress.value = Localization.get("canceling", lang)
    }

    // Reset database cache
    fun clearCache() {
        val lang = settingsManager.getAiLanguage()
        viewModelScope.launch {
            repository.clearAllApps()
            _selectedApp.value = null
            Toast.makeText(context, Localization.get("clear_cache_success", lang), Toast.LENGTH_SHORT).show()
        }
    }
}

class AppLauncherViewModelFactory(
    private val repository: AppRepository,
    private val settingsManager: SettingsManager,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppLauncherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppLauncherViewModel(repository, settingsManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
