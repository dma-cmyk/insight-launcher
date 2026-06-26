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
import com.example.data.UsageTracker
import com.example.data.getParsedEmbedding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import android.util.Log

class AppLauncherViewModel(
    private val repository: AppRepository,
    val settingsManager: SettingsManager,
    private val context: Context
) : ViewModel() {

    private val TAG = "AppLauncherViewModel"
    private var isAnalysisCancelled = false
    val usageTracker = UsageTracker(context)

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

    private val _isVectorSearchEnabled = MutableStateFlow(false)
    val isVectorSearchEnabled: StateFlow<Boolean> = _isVectorSearchEnabled.asStateFlow()

    private val _isVectorSearching = MutableStateFlow(false)
    val isVectorSearching: StateFlow<Boolean> = _isVectorSearching.asStateFlow()

    private val _queryEmbedding = MutableStateFlow<List<Float>?>(null)
    val queryEmbedding: StateFlow<List<Float>?> = _queryEmbedding.asStateFlow()

    // Expose background image URL reactively
    val currentBgUrl: StateFlow<String> = settingsManager.bgImageUrl
    val viewMode: StateFlow<String> = settingsManager.viewMode
    val lastLaunchTimes: StateFlow<Map<String, Long>> = usageTracker.lastLaunchTimes
    val launchCounts: StateFlow<Map<String, Int>> = usageTracker.launchCounts
    val favorites: StateFlow<Set<String>> = usageTracker.favorites

    // Combine installed apps with search query and vector search settings
    val appListState: StateFlow<List<InstalledApp>> = combine(
        repository.getInstalledAppsFlow(context),
        _searchQuery,
        _isVectorSearchEnabled,
        _queryEmbedding
    ) { apps, query, isVectorEnabled, qEmbedding ->
        if (query.isBlank()) {
            apps
        } else if (isVectorEnabled) {
            if (qEmbedding != null) {
                apps.map { app ->
                    val appEmbed = app.cachedInfo?.getParsedEmbedding()
                    val score = if (!appEmbed.isNullOrEmpty()) {
                        cosineSimilarity(qEmbedding, appEmbed)
                    } else {
                        // Fallback similarity calculation for unanalyzed apps (text overlap)
                        val cleanQuery = query.lowercase()
                        val cleanLabel = app.label.lowercase()
                        if (cleanLabel.contains(cleanQuery) || app.packageName.lowercase().contains(cleanQuery)) {
                            0.65f
                        } else {
                            val labelSet = cleanLabel.toSet()
                            val querySet = cleanQuery.toSet()
                            val intersection = labelSet.intersect(querySet).size
                            val union = labelSet.union(querySet).size
                            if (union > 0) 0.3f + (intersection.toFloat() / union) * 0.3f else 0.1f
                        }
                    }
                    val finalScore = (score * 100f).coerceIn(0f, 100f)
                    app.copy(similarityScore = finalScore)
                }.sortedByDescending { it.similarityScore ?: 0f }
            } else {
                // If query embedding is still loading, show all apps without scores
                apps.map { it.copy(similarityScore = null) }
            }
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true) ||
                (it.cachedInfo?.category?.contains(query, ignoreCase = true) ?: false) ||
                (it.cachedInfo?.tags?.contains(query, ignoreCase = true) ?: false)
            }.map { it.copy(similarityScore = null) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Run background backfill of embeddings for existing apps
        ensureEmbeddingsForAnalyzedApps()
    }

    private fun ensureEmbeddingsForAnalyzedApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apps = repository.getAllAppInfosDirect()
                val customApiKey = settingsManager.getGeminiApiKey()
                apps.forEach { appInfo ->
                    if (appInfo.embedding.isNullOrBlank() && appInfo.category.isNotBlank()) {
                        Log.d(TAG, "Backfilling missing embedding for ${appInfo.label}")
                        val embedText = "Label: ${appInfo.label}. Category: ${appInfo.category}. Summary: ${appInfo.summary}. Tags: ${appInfo.tags}"
                        val vector = com.example.data.GeminiClient.getEmbedding(embedText, customApiKey)
                        if (vector != null) {
                            val updated = appInfo.copy(embedding = vector.joinToString(","))
                            repository.updateAppInfo(updated)
                            Log.d(TAG, "Successfully backfilled embedding for ${appInfo.label}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed backfilling embeddings", e)
            }
        }
    }

    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0f
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val denominator = kotlin.math.sqrt(normA.toDouble()) * kotlin.math.sqrt(normB.toDouble())
        return if (denominator == 0.0) 0f else (dotProduct / denominator).toFloat()
    }

    private val _isCorrectingVoice = MutableStateFlow(false)
    val isCorrectingVoice: StateFlow<Boolean> = _isCorrectingVoice.asStateFlow()

    fun processVoiceInput(spokenText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCorrectingVoice.value = true
            try {
                val apiKey = settingsManager.getGeminiApiKey()
                val modelName = settingsManager.getPrimaryModel()
                
                val corrected = com.example.data.GeminiClient.correctVoiceInput(
                    spokenText = spokenText,
                    modelName = modelName,
                    customApiKey = apiKey
                )
                
                if (!corrected.isNullOrBlank()) {
                    _searchQuery.value = corrected
                } else {
                    _searchQuery.value = spokenText
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice input", e)
                _searchQuery.value = spokenText
            } finally {
                _isCorrectingVoice.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (_isVectorSearchEnabled.value) {
            _isVectorSearchEnabled.value = false
            _queryEmbedding.value = null
        }
    }

    fun executeVectorSearch() {
        val query = _searchQuery.value
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _isVectorSearchEnabled.value = true
            _isVectorSearching.value = true
            try {
                val apiKey = settingsManager.getGeminiApiKey()
                val vector = com.example.data.GeminiClient.getEmbedding(query, apiKey)
                _queryEmbedding.value = vector
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching query embedding", e)
                _queryEmbedding.value = null
            } finally {
                _isVectorSearching.value = false
            }
        }
    }

    fun selectApp(app: InstalledApp?) {
        _selectedApp.value = app
    }

    fun setViewMode(mode: String) {
        settingsManager.setViewMode(mode)
    }

    fun toggleFavorite(packageName: String) {
        usageTracker.toggleFavorite(packageName)
    }

    private val _isMergingCategories = MutableStateFlow(false)
    val isMergingCategories: StateFlow<Boolean> = _isMergingCategories.asStateFlow()

    fun mergeCategories() {
        if (_isMergingCategories.value) return
        viewModelScope.launch {
            _isMergingCategories.value = true
            val success = repository.mergeCategories(
                modelName = settingsManager.getPrimaryModel(),
                customApiKey = settingsManager.getGeminiApiKey(),
                languageCode = settingsManager.getAiLanguage()
            )
            _isMergingCategories.value = false
            if (success) {
                val lang = settingsManager.getAiLanguage()
                val msg = if (lang == "ja") "カテゴリの統合が完了しました" else "Categories merged successfully"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else {
                val lang = settingsManager.getAiLanguage()
                val msg = if (lang == "ja") "カテゴリの統合に失敗しました" else "Failed to merge categories"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launch app on device
    fun launchApp(packageName: String) {
        val lang = settingsManager.getAiLanguage()
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                usageTracker.recordLaunch(packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, Localization.get("launch_no_intent", lang), Toast.LENGTH_SHORT).show()
                openAppSettings(packageName)
            }
        } catch (e: Exception) {
            Toast.makeText(context, Localization.get("init_error", lang, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    // Open App Settings screen in Android OS settings
    fun openAppSettings(packageName: String) {
        val lang = settingsManager.getAiLanguage()
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
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

            if (!isAnalysisCancelled && successCount > 0) {
                _analysisProgress.value = Localization.get("merging_categories_status", lang)
                try {
                    repository.mergeCategories(
                        modelName = settingsManager.getPrimaryModel(),
                        customApiKey = settingsManager.getGeminiApiKey(),
                        languageCode = settingsManager.getAiLanguage()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed auto merge categories after batch analysis", e)
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
