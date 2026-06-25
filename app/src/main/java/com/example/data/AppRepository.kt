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
    val cachedInfo: AppInfo? = null
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
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos.mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()
            val isSystemApp = (activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
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

            val appInfo = AppInfo(
                packageName = packageName,
                label = label,
                category = analysis.category,
                summary = analysis.summary,
                tags = tagsCsv,
                relatedLinks = linksJson,
                isSystemApp = isSystemApp,
                lastUpdated = System.currentTimeMillis()
            )

            appDao.insertApp(appInfo)
            Log.d(TAG, "Successfully cached AI analysis for $label")
            true
        } else {
            Log.e(TAG, "Failed to analyze app: $label")
            false
        }
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
}
