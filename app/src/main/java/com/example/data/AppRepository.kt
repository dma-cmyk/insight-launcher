package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val cachedInfo: AppInfo? = null,
    val similarityScore: Float? = null
) {
    val isAnalyzed: Boolean get() = cachedInfo != null
}

class AppRepository(private val appDao: AppDao) {

    companion object {
        private const val TAG = "AppRepository"
    }

    private val refreshTrigger = MutableStateFlow(0L)

    fun refreshInstalledApps() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    suspend fun syncDatabaseWithInstalledApps(context: Context) = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val installedPackages = getLaunchableAppsList(pm).map { it.packageName }.toSet()
            val cachedApps = appDao.getAllAppsDirect()
            
            val appsToDelete = cachedApps.filter { !installedPackages.contains(it.packageName) }
            if (appsToDelete.isNotEmpty()) {
                Log.d(TAG, "Deleting ${appsToDelete.size} uninstalled apps from DB: ${appsToDelete.map { it.packageName }}")
                appsToDelete.forEach { app ->
                    appDao.deleteAppByPackageName(app.packageName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync database with installed apps: ${e.message}", e)
        }
    }

    // Get reactive flow combining PM list and Room database list
    fun getInstalledAppsFlow(context: Context): Flow<List<InstalledApp>> {
        val pm = context.packageManager
        return combine(
            appDao.getAllAppsFlow(),
            refreshTrigger
        ) { cachedList, _ ->
            val pmList = getLaunchableAppsList(pm)
            val cachedMap = cachedList.associateBy { it.packageName }
            pmList.map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.label,
                    isSystemApp = info.isSystemApp,
                    cachedInfo = cachedMap[info.packageName]
                )
            }.sortedWith(compareBy<InstalledApp> { !it.isAnalyzed }.thenBy { it.label.lowercase() })
        }
    }

    // Get raw launchable apps using Package Manager
    private fun getLaunchableAppsList(pm: PackageManager): List<PmAppInfo> {
        val apps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }
        return apps.mapNotNull { appInfo ->
            val packageName = appInfo.packageName
            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            PmAppInfo(packageName, label, isSystemApp)
        }.distinctBy { it.packageName }
    }

    // Clears the entire database cache of analyzed apps
    suspend fun clearAllApps() = withContext(Dispatchers.IO) {
        appDao.clearAllApps()
    }

    // Run AI analysis on a single app and save to Room
    suspend fun analyzeAndCacheApp(
        context: Context,
        packageName: String,
        label: String,
        isSystemApp: Boolean,
        settingsManager: SettingsManager,
        userContextText: String? = null,
        fileName: String? = null,
        fileMimeType: String? = null,
        fileBytes: ByteArray? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val primaryModel = settingsManager.getPrimaryModel()
        val backupModel = settingsManager.getBackupModel()
        val aiLanguage = settingsManager.getAiLanguage()
        val customApiKey = settingsManager.getGeminiApiKey()

        Log.d(TAG, "Starting AI analysis for $label ($packageName)...")
        val analysis = GeminiClient.analyzeApp(
            appName = label,
            packageName = packageName,
            modelName = primaryModel,
            backupModelName = backupModel,
            languageCode = aiLanguage,
            customApiKey = customApiKey,
            userContextText = userContextText,
            fileName = fileName,
            fileMimeType = fileMimeType,
            fileBytes = fileBytes
        )

        if (analysis != null) {
            // Serialize tags list as comma separated
            val tagsCsv = analysis.tags.joinToString(",")
            
            // Serialize relatedLinks as JSON
            val linksJson = serializeLinks(analysis.relatedLinks)

            // Try to fetch embedding for semantic search
            val embedText = "Label: $label. Category: ${analysis.category}. Summary: ${analysis.summary}. Tags: $tagsCsv"
            val embeddingVector = try {
                GeminiClient.getEmbedding(embedText, customApiKey)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching embedding vector: ${e.message}")
                null
            }
            val embeddingCsv = embeddingVector?.joinToString(",")

            val appInfo = AppInfo(
                packageName = packageName,
                label = label,
                category = analysis.category,
                summary = analysis.summary,
                tags = tagsCsv,
                relatedLinks = linksJson,
                isSystemApp = isSystemApp,
                lastUpdated = System.currentTimeMillis(),
                embedding = embeddingCsv
            )

            appDao.insertApp(appInfo)
            Log.d(TAG, "Successfully cached AI analysis for $label")
            true
        } else {
            Log.e(TAG, "Failed to analyze app: $label")
            false
        }
    }

    // Run AI analysis on multiple apps in bulk and save to Room
    suspend fun analyzeAndCacheAppsBulk(
        context: Context,
        appsToAnalyze: List<com.example.data.AppToAnalyze>,
        settingsManager: SettingsManager,
        isCancelled: () -> Boolean,
        onProgress: (currentIndex: Int, total: Int, currentBatchNames: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val primaryModel = settingsManager.getPrimaryModel()
        val backupModel = settingsManager.getBackupModel()
        val aiLanguage = settingsManager.getAiLanguage()
        val customApiKey = settingsManager.getGeminiApiKey()

        val batchSize = 10
        var successCount = 0
        val total = appsToAnalyze.size

        val pm = context.packageManager
        val systemPackageNames = try {
            pm.getInstalledPackages(PackageManager.GET_META_DATA).associate { 
                it.packageName to (it.applicationInfo?.let { info -> (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false)
            }
        } catch (e: Exception) {
            emptyMap()
        }

        val chunks = appsToAnalyze.chunked(batchSize)
        var processedCount = 0

        for ((chunkIndex, chunk) in chunks.withIndex()) {
            if (isCancelled()) break
            val names = chunk.joinToString(", ") { it.label }
            onProgress(processedCount, total, names)

            Log.d(TAG, "Starting bulk AI analysis for batch ${chunkIndex + 1}/${chunks.size} containing: $names")
            val results = GeminiClient.analyzeAppsBulk(
                apps = chunk,
                modelName = primaryModel,
                backupModelName = backupModel,
                languageCode = aiLanguage,
                customApiKey = customApiKey
            )

            if (isCancelled()) break

            if (results != null) {
                for (result in results) {
                    if (isCancelled()) break
                    val appToAnalyze = chunk.find { it.packageName == result.packageName }
                    val label = appToAnalyze?.label ?: result.packageName
                    val isSystemApp = systemPackageNames[result.packageName] ?: false

                    val tagsCsv = result.tags.joinToString(",")
                    val linksJson = serializeLinks(result.relatedLinks)

                    val embedText = "Label: $label. Category: ${result.category}. Summary: ${result.summary}. Tags: $tagsCsv"
                    val embeddingVector = try {
                        GeminiClient.getEmbedding(embedText, customApiKey)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching embedding vector in bulk: ${e.message}")
                        null
                    }
                    val embeddingCsv = embeddingVector?.joinToString(",")

                    val appInfo = AppInfo(
                        packageName = result.packageName,
                        label = label,
                        category = result.category,
                        summary = result.summary,
                        tags = tagsCsv,
                        relatedLinks = linksJson,
                        isSystemApp = isSystemApp,
                        lastUpdated = System.currentTimeMillis(),
                        embedding = embeddingCsv
                    )

                    appDao.insertApp(appInfo)
                    successCount++
                }
            } else {
                Log.e(TAG, "Failed bulk analysis for batch ${chunkIndex + 1}/${chunks.size}. Falling back to individual sequential analysis for this batch...")
                for (app in chunk) {
                    if (isCancelled()) break
                    val isSystemApp = systemPackageNames[app.packageName] ?: false
                    val success = analyzeAndCacheApp(
                        context = context,
                        packageName = app.packageName,
                        label = app.label,
                        isSystemApp = isSystemApp,
                        settingsManager = settingsManager
                    )
                    if (success) {
                        successCount++
                    }
                    delay(1500)
                }
            }

            processedCount += chunk.size

            if (chunkIndex < chunks.size - 1) {
                // Wait 4 seconds, but check cancellation every 500ms
                for (i in 0 until 8) {
                    if (isCancelled()) break
                    delay(500)
                }
            }
        }

        successCount
    }

    suspend fun updateAppInfo(appInfo: AppInfo) = withContext(Dispatchers.IO) {
        appDao.insertApp(appInfo)
    }

    suspend fun getAllAppInfosDirect(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedPackageNames = getLaunchableAppsList(pm).map { it.packageName }.toSet()
        appDao.getAllAppsDirect().filter { installedPackageNames.contains(it.packageName) }
    }

    private fun serializeLinks(links: List<RelatedLink>): String {
        val array = JSONArray()
        links.forEach { link ->
            val obj = JSONObject()
            obj.put("title", link.title)
            obj.put("url", link.url)
            array.put(obj)
        }
        return array.toString()
    }

    private data class PmAppInfo(
        val packageName: String,
        val label: String,
        val isSystemApp: Boolean
    )

    suspend fun mergeCategories(
        modelName: String,
        customApiKey: String? = null,
        languageCode: String = "ja"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val distinctCategories = appDao.getDistinctCategories()
            if (distinctCategories.isEmpty()) return@withContext false

            val mergeMap = GeminiClient.mergeCategories(
                categories = distinctCategories,
                modelName = modelName,
                customApiKey = customApiKey,
                languageCode = languageCode
            )

            if (mergeMap != null && mergeMap.isNotEmpty()) {
                val allApps = appDao.getAllAppsDirect()
                val updatedApps = allApps.map { app ->
                    val newCategory = mergeMap[app.category]
                    if (newCategory != null && newCategory != app.category) {
                        app.copy(category = newCategory)
                    } else {
                        app
                    }
                }
                appDao.insertApps(updatedApps)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error merging categories", e)
            false
        }
    }

    // LLM Wiki (AI Memory) operations
    val allWikiEntriesFlow: Flow<List<LlmWikiEntry>> = appDao.getAllWikiEntriesFlow()

    suspend fun getAllWikiEntriesDirect(): List<LlmWikiEntry> = withContext(Dispatchers.IO) {
        appDao.getAllWikiEntriesDirect()
    }

    suspend fun insertWikiEntry(entry: LlmWikiEntry) = withContext(Dispatchers.IO) {
        appDao.insertWikiEntry(entry)
    }

    suspend fun deleteWikiEntryById(id: Long) = withContext(Dispatchers.IO) {
        appDao.deleteWikiEntryById(id)
    }

    suspend fun clearAllWikiEntries() = withContext(Dispatchers.IO) {
        appDao.clearAllWikiEntries()
    }

    // MCP Server operations
    val allMcpServersFlow: Flow<List<McpServerEntity>> = appDao.getAllMcpServersFlow()

    suspend fun getAllMcpServersDirect(): List<McpServerEntity> = withContext(Dispatchers.IO) {
        appDao.getAllMcpServersDirect()
    }

    suspend fun insertMcpServer(server: McpServerEntity) = withContext(Dispatchers.IO) {
        appDao.insertMcpServer(server)
    }

    suspend fun deleteMcpServerById(id: Long) = withContext(Dispatchers.IO) {
        appDao.deleteMcpServerById(id)
    }
}
