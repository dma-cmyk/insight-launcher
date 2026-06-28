package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppInfo
import com.example.data.AppRepository
import com.example.data.LlmWikiEntry
import com.example.data.InstalledApp
import com.example.data.SettingsManager
import com.example.data.UsageTracker
import com.example.data.getParsedEmbedding
import com.example.data.McpManager
import com.example.data.McpServerEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import android.util.Log
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

class AppLauncherViewModel(
    private val repository: AppRepository,
    val settingsManager: SettingsManager,
    private val context: Context
) : ViewModel() {

    private val TAG = "AppLauncherViewModel"
    private var isAnalysisCancelled = false
    val usageTracker = UsageTracker(context)
    val mcpManager = McpManager(context, repository, settingsManager)

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

    private val _bgLuminance = MutableStateFlow(0.05f) // Default is dark (procedural nebula)
    val bgLuminance: StateFlow<Float> = _bgLuminance.asStateFlow()

    val autoContrast: StateFlow<Boolean> = settingsManager.autoContrast

    // Expose background image URL reactively
    val currentBgUrl: StateFlow<String> = settingsManager.bgImageUrl
    val viewMode: StateFlow<String> = settingsManager.viewMode
    val iconShape: StateFlow<String> = settingsManager.iconShape
    val colorTheme: StateFlow<String> = settingsManager.colorTheme
    val lastLaunchTimes: StateFlow<Map<String, Long>> = usageTracker.lastLaunchTimes
    val launchCounts: StateFlow<Map<String, Int>> = usageTracker.launchCounts
    val favorites: StateFlow<List<String>> = usageTracker.favorites
    val customIcons: StateFlow<Map<String, String>> = usageTracker.customIcons

    // Unfiltered installed apps stream for stable category calculations during search
    val allAppsState: StateFlow<List<InstalledApp>> = repository.getInstalledAppsFlow(context, settingsManager.includeIconlessSystemApps).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Combine installed apps with search query and vector search settings
    val appListState: StateFlow<List<InstalledApp>> = combine(
        repository.getInstalledAppsFlow(context, settingsManager.includeIconlessSystemApps),
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
        // Register BroadcastReceiver for package added, removed, changed
        try {
            val packageFilter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
                addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            val packageReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(receiverContext: android.content.Context?, intent: android.content.Intent?) {
                    Log.d(TAG, "Package broadcast received: ${intent?.action}")
                    refreshInstalledApps()
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(packageReceiver, packageFilter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(packageReceiver, packageFilter)
            }
            Log.d(TAG, "Successfully registered package event receiver.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register package receiver: ${e.message}", e)
        }

        // Run background backfill of embeddings for existing apps
        ensureEmbeddingsForAnalyzedApps()

        // Start background image luminance tracking
        startBgLuminanceAnalysis()
    }

    private fun ensureEmbeddingsForAnalyzedApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.syncDatabaseWithInstalledApps(context, settingsManager.getIncludeIconlessSystemApps())
                val apps = repository.getAllAppInfosDirect(context, settingsManager.getIncludeIconlessSystemApps())
                val customApiKey = settingsManager.getGeminiApiKey()
                apps.forEach { appInfo ->
                    if (appInfo.embedding.isNullOrBlank() && appInfo.category.isNotBlank()) {
                        Log.d(TAG, "Backfilling missing embedding for ${appInfo.label}")
                        val embedText = "Label: ${appInfo.label}. Category: ${appInfo.category}. Summary: ${appInfo.summary}. Tags: ${appInfo.tags}"
                        val embeddingModel = settingsManager.getEmbeddingModel()
                        val vector = com.example.data.GeminiClient.getEmbedding(embedText, customApiKey, modelName = embeddingModel)
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
                val embeddingModel = settingsManager.getEmbeddingModel()
                val vector = com.example.data.GeminiClient.getEmbedding(query, apiKey, modelName = embeddingModel)
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

    fun setIconShape(shape: String) {
        settingsManager.setIconShape(shape)
    }

    fun setColorTheme(theme: String) {
        settingsManager.setColorTheme(theme)
    }

    fun toggleFavorite(packageName: String) {
        usageTracker.toggleFavorite(packageName)
    }

    fun saveFavoritesOrder(newList: List<String>) {
        usageTracker.saveFavorites(newList)
    }

    fun setCustomIcon(packageName: String, icon: String?) {
        usageTracker.setCustomIcon(packageName, icon)
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
                    userContextText = userContextText ?: settingsManager.getCustomCategorizationPrompt(),
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

    // Bulk auto-analysis of all unanalyzed apps
    fun autoAnalyzeAllUnanalyzedBulk() {
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

            val appsToAnalyze = unanalyzed.map { com.example.data.AppToAnalyze(it.packageName, it.label) }

            val initialStatus = if (lang == "ja") {
                "一括解析の準備中..."
            } else if (lang == "ko") {
                "일괄 분석 준비 중..."
            } else if (lang == "zh") {
                "批量分析准备中..."
            } else {
                "Preparing bulk analysis..."
            }
            _analysisProgress.value = initialStatus

            try {
                successCount = repository.analyzeAndCacheAppsBulk(
                    context = context,
                    appsToAnalyze = appsToAnalyze,
                    settingsManager = settingsManager,
                    isCancelled = { isAnalysisCancelled },
                    onProgress = { currentIndex, totalCount, statusText ->
                        _analysisProgressPercent.value = currentIndex.toFloat() / totalCount
                        _analysisProgress.value = statusText
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Bulk auto-analysis failed", e)
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

    // Sequential auto-analysis of all unanalyzed apps with rate limit resilience and retries
    fun autoAnalyzeAllUnanalyzedSequential() {
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
                        delay(1000)
                    }
                    
                    success = repository.analyzeAndCacheApp(
                        context = context,
                        packageName = app.packageName,
                        label = app.label,
                        isSystemApp = app.isSystemApp,
                        settingsManager = settingsManager,
                        userContextText = settingsManager.getCustomCategorizationPrompt(),
                        onStatusUpdate = { subStatus ->
                            _analysisProgress.value = "(${index + 1}/$total) $subStatus"
                        }
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

    // Reset analysis data only
    fun resetAllAnalysis() {
        viewModelScope.launch {
            repository.resetAllAnalysis()
            val msg = when (settingsManager.getAiLanguage()) {
                "ja" -> "すべての解析データをリセットしました"
                "ko" -> "모든 분석 데이터를 초기화했습니다"
                "zh" -> "已重置所有分析数据"
                else -> "All analysis data reset"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    
    fun reanalyzeAllBulk() {
        viewModelScope.launch {
            repository.resetAllAnalysis()
            autoAnalyzeAllUnanalyzedBulk()
        }
    }
    
    fun reanalyzeAllSequential() {
        viewModelScope.launch {
            repository.resetAllAnalysis()
            autoAnalyzeAllUnanalyzedSequential()
        }
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

    private val _isAssistantLoading = MutableStateFlow(false)
    val isAssistantLoading: StateFlow<Boolean> = _isAssistantLoading.asStateFlow()

    private val _assistantResponse = MutableStateFlow<com.example.data.GeminiAssistantResponse?>(null)
    val assistantResponse: StateFlow<com.example.data.GeminiAssistantResponse?> = _assistantResponse.asStateFlow()

    private val _githubRepos = MutableStateFlow<List<com.example.data.GitHubRepo>>(emptyList())
    val githubRepos: StateFlow<List<com.example.data.GitHubRepo>> = _githubRepos.asStateFlow()

    private val _isGithubLoading = MutableStateFlow(false)
    val isGithubLoading: StateFlow<Boolean> = _isGithubLoading.asStateFlow()
    
    private val _fdroidRepos = MutableStateFlow<List<com.example.data.FDroidPackage>>(emptyList())
    val fdroidRepos: StateFlow<List<com.example.data.FDroidPackage>> = _fdroidRepos.asStateFlow()

    private val _isFDroidLoading = MutableStateFlow(false)
    val isFDroidLoading: StateFlow<Boolean> = _isFDroidLoading.asStateFlow()

    private val _githubRepoDetail = MutableStateFlow<com.example.data.GitHubRepoDetails?>(null)
    val githubRepoDetail: StateFlow<com.example.data.GitHubRepoDetails?> = _githubRepoDetail.asStateFlow()

    private val _fdroidAppDetail = MutableStateFlow<com.example.data.FDroidDetails?>(null)
    val fdroidAppDetail: StateFlow<com.example.data.FDroidDetails?> = _fdroidAppDetail.asStateFlow()

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading: StateFlow<Boolean> = _isDetailLoading.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    fun loadGitHubRepoDetails(repo: com.example.data.GitHubRepo) {
        viewModelScope.launch {
            _isDetailLoading.value = true
            _detailError.value = null
            _githubRepoDetail.value = null
            _fdroidAppDetail.value = null
            try {
                val details = com.example.data.GitHubClient.fetchRepoDetails(
                    repo = repo,
                    targetLanguageCode = settingsManager.getAiLanguage(),
                    modelName = settingsManager.getPrimaryModel(),
                    customApiKey = settingsManager.getGeminiApiKey()
                )
                _githubRepoDetail.value = details
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load GitHub repo details", e)
                _detailError.value = e.localizedMessage ?: "Failed to load details"
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    fun loadFDroidAppDetails(pkg: com.example.data.FDroidPackage) {
        viewModelScope.launch {
            _isDetailLoading.value = true
            _detailError.value = null
            _githubRepoDetail.value = null
            _fdroidAppDetail.value = null
            try {
                val details = com.example.data.FDroidClient.fetchAppDetails(
                    packageName = pkg.packageName,
                    name = pkg.name,
                    summary = pkg.summary,
                    iconUrl = pkg.iconUrl,
                    license = pkg.license,
                    targetLanguageCode = settingsManager.getAiLanguage(),
                    modelName = settingsManager.getPrimaryModel(),
                    customApiKey = settingsManager.getGeminiApiKey()
                )
                _fdroidAppDetail.value = details
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load F-Droid details", e)
                _detailError.value = e.localizedMessage ?: "Failed to load details"
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    fun clearDetailState() {
        _githubRepoDetail.value = null
        _fdroidAppDetail.value = null
        _isDetailLoading.value = false
        _detailError.value = null
    }

    fun searchGitHub(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isGithubLoading.value = true
            try {
                val searchResult = com.example.data.GitHubClient.service.searchRepositories(query)
                val targetLang = settingsManager.getAiLanguage()
                val topRepos = searchResult.items.take(10)
                val translatedRepos = com.example.data.GeminiClient.translateGitHubRepos(
                    repos = topRepos,
                    targetLanguageCode = targetLang,
                    modelName = settingsManager.getPrimaryModel(),
                    customApiKey = settingsManager.getGeminiApiKey()
                )
                _githubRepos.value = translatedRepos
            } catch (e: Exception) {
                Log.e(TAG, "GitHub search failed: ${e.message}", e)
                _githubRepos.value = emptyList()
            } finally {
                _isGithubLoading.value = false
            }
        }
    }

    fun searchFDroid(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isFDroidLoading.value = true
            try {
                val topPackages = com.example.data.FDroidClient.search(query).take(10)
                // F-Droid search doesn't strictly need LLM translation since it's mostly english and simple, 
                // but we can translate descriptions or just use as-is to save latency
                val targetLang = settingsManager.getAiLanguage()
                val translated = if (targetLang != "en") {
                    com.example.data.GeminiClient.translateFDroidPackages(
                         packages = topPackages,
                         targetLanguageCode = targetLang,
                         modelName = settingsManager.getPrimaryModel(),
                         customApiKey = settingsManager.getGeminiApiKey()
                    )
                } else {
                    topPackages
                }
                _fdroidRepos.value = translated
            } catch (e: Exception) {
                Log.e(TAG, "F-Droid search failed: ${e.message}", e)
                _fdroidRepos.value = emptyList()
            } finally {
                _isFDroidLoading.value = false
            }
        }
    }

    val wikiEntries: StateFlow<List<LlmWikiEntry>> = repository.allWikiEntriesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveWikiEntry(entry: LlmWikiEntry) {
        viewModelScope.launch {
            repository.insertWikiEntry(entry)
        }
    }

    fun deleteWikiEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteWikiEntryById(id)
            // Cleanup related link IDs in other entries
            val allEntries = repository.getAllWikiEntriesDirect()
            allEntries.forEach { entry ->
                if (entry.relatedLinkIds.contains(id)) {
                    val updatedIds = entry.relatedLinkIds.filter { it != id }
                    repository.insertWikiEntry(entry.copy(relatedLinkIds = updatedIds))
                }
            }
        }
    }

    fun clearAllWikiEntries() {
        viewModelScope.launch {
            repository.clearAllWikiEntries()
        }
    }

    private val _isAutoLinking = MutableStateFlow(false)
    val isAutoLinking: StateFlow<Boolean> = _isAutoLinking.asStateFlow()

    fun autoLinkWikiEntries() {
        viewModelScope.launch {
            _isAutoLinking.value = true
            try {
                val currentWikis = repository.getAllWikiEntriesDirect()
                if (currentWikis.size < 2) return@launch
                
                val customApiKey = settingsManager.getGeminiApiKey()
                val modelName = settingsManager.getPrimaryModel()
                
                val updatedWikis = com.example.data.GeminiClient.autoLinkWikis(
                    wikis = currentWikis,
                    modelName = modelName,
                    customApiKey = customApiKey
                )
                
                updatedWikis.forEach {
                    repository.insertWikiEntry(it)
                }
                
                val lang = settingsManager.getAiLanguage()
                val msg = if (lang == "ja") "記憶の関連リンクを自動整理しました" else "Successfully auto-linked memories"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error auto linking wikis", e)
                val lang = settingsManager.getAiLanguage()
                val msg = if (lang == "ja") "自動整理に失敗しました: ${e.localizedMessage}" else "Failed to auto-link: ${e.localizedMessage}"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _isAutoLinking.value = false
            }
        }
    }

    private val _isBulkOrganizing = MutableStateFlow(false)
    val isBulkOrganizing: StateFlow<Boolean> = _isBulkOrganizing.asStateFlow()

    fun bulkOrganizeWikiEntries() {
        viewModelScope.launch {
            _isBulkOrganizing.value = true
            try {
                val currentWikis = repository.getAllWikiEntriesDirect()
                if (currentWikis.isEmpty()) return@launch
                
                val customApiKey = settingsManager.getGeminiApiKey()
                val modelName = settingsManager.getPrimaryModel()
                
                val updatedWikis = com.example.data.GeminiClient.bulkOrganizeWikis(
                    wikis = currentWikis,
                    modelName = modelName,
                    customApiKey = customApiKey
                )
                
                updatedWikis.forEach {
                    repository.insertWikiEntry(it)
                }
                
                val lang = settingsManager.getAiLanguage()
                val msg = if (lang == "ja") "記憶のカテゴリーとタグを一括整理しました" else "Successfully organized memories"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error bulk organizing wikis", e)
                val lang = settingsManager.getAiLanguage()
                val msg = if (lang == "ja") "一括整理に失敗しました: ${e.localizedMessage}" else "Failed to organize: ${e.localizedMessage}"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _isBulkOrganizing.value = false
            }
        }
    }

    private val _isExtractingWiki = MutableStateFlow(false)
    val isExtractingWiki: StateFlow<Boolean> = _isExtractingWiki.asStateFlow()

    fun extractAndSaveWikiFromConversation(userPrompt: String, aiAnswer: String) {
        viewModelScope.launch {
            _isExtractingWiki.value = true
            try {
                val customApiKey = settingsManager.getGeminiApiKey()
                val modelName = settingsManager.getPrimaryModel()
                val lang = settingsManager.getAiLanguage()
                
                val entry = com.example.data.GeminiClient.extractWikiEntry(
                    userPrompt = userPrompt,
                    aiAnswer = aiAnswer,
                    modelName = modelName,
                    customApiKey = customApiKey,
                    languageCode = lang
                )
                if (entry != null) {
                    repository.insertWikiEntry(entry)
                    val msg = if (lang == "ja") "会話からWikiエントリーを抽出して保存しました: ${entry.title}" else "Extracted and saved Wiki entry: ${entry.title}"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } else {
                    val msg = if (lang == "ja") "Wikiエントリーを抽出できませんでした（APIキーや返答を確認してください）" else "Could not extract Wiki entry"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting Wiki", e)
                val lang = settingsManager.getAiLanguage()
                val msg = if (lang == "ja") "エラーが発生しました: ${e.localizedMessage}" else "Error: ${e.localizedMessage}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } finally {
                _isExtractingWiki.value = false
            }
        }
    }

    fun processAiAssistantVoiceInput(spokenText: String, onCorrected: (String) -> Unit) {
        viewModelScope.launch {
            _isAssistantLoading.value = true
            try {
                val apiKey = settingsManager.getGeminiApiKey()
                val modelName = settingsManager.getPrimaryModel()
                
                val corrected = com.example.data.GeminiClient.correctVoiceInput(
                    spokenText = spokenText,
                    modelName = modelName,
                    customApiKey = apiKey
                )
                
                val finalQuery = if (!corrected.isNullOrBlank()) corrected else spokenText
                onCorrected(finalQuery)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing AI assistant voice input", e)
                onCorrected(spokenText)
            } finally {
                // We don't set _isAssistantLoading = false here because askAiAssistant will be called right after
                // and it will manage the loading state.
            }
        }
    }

    fun askAiAssistant(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isAssistantLoading.value = true
            try {
                val apps = repository.getAllAppInfosDirect(context, settingsManager.getIncludeIconlessSystemApps())
                val customApiKey = settingsManager.getGeminiApiKey()
                val modelName = settingsManager.getPrimaryModel()
                val lang = settingsManager.getAiLanguage()
                val currentWikis = repository.getAllWikiEntriesDirect()
                
                val response = com.example.data.GeminiClient.askAssistant(
                    userInstruction = query,
                    apps = apps,
                    wikiEntries = currentWikis,
                    modelName = modelName,
                    customApiKey = customApiKey,
                    languageCode = lang,
                    mcpManager = mcpManager,
                    favorites = favorites.value,
                    lastLaunchTimes = lastLaunchTimes.value,
                    launchCounts = launchCounts.value
                )
                _assistantResponse.value = response
                val ghQuery = response?.githubSearchQuery
                if (!ghQuery.isNullOrBlank()) {
                    searchGitHub(ghQuery)
                } else {
                    _githubRepos.value = emptyList()
                }

                val fdQuery = response?.fdroidSearchQuery
                if (!fdQuery.isNullOrBlank()) {
                    searchFDroid(fdQuery)
                } else {
                    _fdroidRepos.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in askAiAssistant", e)
                val lang = settingsManager.getAiLanguage()
                _assistantResponse.value = com.example.data.GeminiAssistantResponse(
                    headline = if (lang == "ja") "エラーが発生しました" else "Error occurred",
                    answer = if (lang == "ja") "リクエストの処理中にエラーが発生しました: ${e.localizedMessage}" else "Failed to process request: ${e.localizedMessage}",
                    relevantPackages = emptyList(),
                    suggestions = listOf(
                        if (lang == "ja") "再試行" else "Retry",
                        if (lang == "ja") "ホームに戻る" else "Back to Home"
                    )
                )
                _githubRepos.value = emptyList()
            } finally {
                _isAssistantLoading.value = false
            }
        }
    }

    fun clearAssistantState() {
        _assistantResponse.value = null
        _isAssistantLoading.value = false
        _githubRepos.value = emptyList()
    }

    private fun startBgLuminanceAnalysis() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.bgImageUrl.collectLatest { url ->
                if (url == "procedural_nebula") {
                    _bgLuminance.value = 0.05f
                } else {
                    try {
                        val loader = ImageLoader(context)
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .allowHardware(false) // Allow pixel reading
                            .build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) {
                            val drawable = result.drawable
                            if (drawable is BitmapDrawable) {
                                val bitmap = drawable.bitmap
                                val luminance = analyzeBitmapLuminance(bitmap)
                                Log.d(TAG, "Analyzed background luminance for $url: $luminance")
                                _bgLuminance.value = luminance
                            } else {
                                _bgLuminance.value = 0.4f
                            }
                        } else {
                            _bgLuminance.value = 0.4f
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to analyze background image luminance", e)
                        _bgLuminance.value = 0.4f
                    }
                }
            }
        }
    }

    private fun analyzeBitmapLuminance(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val stepX = (width / 40).coerceAtLeast(1)
        val stepY = (height / 40).coerceAtLeast(1)
        var totalLuminance = 0.0
        var count = 0
        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                if (x < width && y < height) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = android.graphics.Color.red(pixel) / 255.0
                    val g = android.graphics.Color.green(pixel) / 255.0
                    val b = android.graphics.Color.blue(pixel) / 255.0
                    val l = 0.2126 * r + 0.7152 * g + 0.0722 * b
                    totalLuminance += l
                    count++
                }
            }
        }
        return if (count > 0) (totalLuminance / count).toFloat() else 0.4f
    }

    val mcpServers: StateFlow<List<McpServerEntity>> = repository.allMcpServersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMcpServer(name: String, description: String, endpointUrl: String) {
        viewModelScope.launch {
            val server = McpServerEntity(
                name = name,
                description = description,
                type = "REMOTE",
                endpointUrl = endpointUrl,
                isEnabled = true,
                lastUpdated = System.currentTimeMillis()
            )
            repository.insertMcpServer(server)
        }
    }

    fun deleteMcpServer(id: Long) {
        viewModelScope.launch {
            repository.deleteMcpServerById(id)
        }
    }

    fun toggleMcpServer(server: McpServerEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.insertMcpServer(server.copy(isEnabled = isEnabled, lastUpdated = System.currentTimeMillis()))
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            repository.syncDatabaseWithInstalledApps(context, settingsManager.getIncludeIconlessSystemApps())
            repository.refreshInstalledApps()
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
