package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    // Get reactive flow combining PM list and Room database list
    fun getInstalledAppsFlow(context: Context): Flow<List<InstalledApp>> {
        val pm = context.packageManager
        val allLaunchers = getLaunchableAppsList(pm)

        return appDao.getAllAppsFlow().combine(flowOfEmptyListIfError(allLaunchers)) { cachedList, pmList ->
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

    private fun flowOfEmptyListIfError(list: List<PmAppInfo>): kotlinx.coroutines.flow.Flow<List<PmAppInfo>> {
        return kotlinx.coroutines.flow.flowOf(list)
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

    suspend fun updateAppInfo(appInfo: AppInfo) = withContext(Dispatchers.IO) {
        appDao.insertApp(appInfo)
    }

    suspend fun getAllAppInfosDirect(): List<AppInfo> = withContext(Dispatchers.IO) {
        appDao.getAllAppsDirect()
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
}
