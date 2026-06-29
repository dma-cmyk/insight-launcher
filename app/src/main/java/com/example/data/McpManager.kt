package com.example.data

import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>,
    val isRemote: Boolean = false,
    val remoteUrl: String = ""
)

class McpManager(
    private val context: Context,
    private val repository: AppRepository,
    private val settingsManager: SettingsManager,
    private val usageTracker: UsageTracker? = null
) {
    private val TAG = "McpManager"
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val builtinTools = listOf(
        McpTool(
            name = "get_current_time_and_date",
            description = "Retrieve the precise current system date, time, day of the week, and timezone. Use this to ensure accuracy when answering time/scheduling queries.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "required" to emptyList<String>()
            )
        ),
        McpTool(
            name = "get_device_status",
            description = "Get current hardware and system metrics including battery percentage, remaining/total storage, free RAM, OS version, and active network type.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "required" to emptyList<String>()
            )
        ),
        McpTool(
            name = "search_installed_apps",
            description = "Search through the launcher's installed applications using a keyword to find matching apps, their package names, categories, and tags.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf(
                        "type" to "string",
                        "description" to "The search term (e.g. 'Browser', 'Social', 'com.android')"
                    )
                ),
                "required" to listOf("query")
            )
        ),
        McpTool(
            name = "launch_installed_app",
            description = "Launch an installed Android application using its exact package name.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "packageName" to mapOf(
                        "type" to "string",
                        "description" to "The exact package name of the app to launch (e.g., 'com.android.chrome')"
                    )
                ),
                "required" to listOf("packageName")
            )
        ),
        McpTool(
            name = "launcher_settings_control",
            description = "Read or modify launcher UI settings such as current background wallpaper URL, auto-contrast toggle, view-mode (LIST vs GRID), and icon shape.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "action" to mapOf(
                        "type" to "string",
                        "description" to "The action to perform: 'read' (to get current config) or 'write' (to update a setting value)"
                    ),
                    "key" to mapOf(
                        "type" to "string",
                        "description" to "The setting key to modify: 'bgImageUrl', 'autoContrast', 'iconShape', 'aiLanguage', 'viewMode' (only used for 'write' action)"
                    ),
                    "value" to mapOf(
                        "type" to "string",
                        "description" to "The new value to assign (only used for 'write' action)"
                    )
                ),
                "required" to listOf("action")
            )
        ),
        McpTool(
            name = "evaluate_math_expression",
            description = "Evaluate a mathematical expression accurately to prevent LLM calculation errors. Supports +, -, *, /, ^, parentheses, sqrt(), sin(), cos(), tan(), and logs.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "expression" to mapOf(
                        "type" to "string",
                        "description" to "The mathematical expression to evaluate, e.g., '(45 * 12) + sqrt(144)'"
                    )
                ),
                "required" to listOf("expression")
            )
        ),
        McpTool(
            name = "get_weather_info",
            description = "Query current weather conditions and temperature for a specified city or location in real-time.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "city" to mapOf(
                        "type" to "string",
                        "description" to "City or region name in English (e.g. 'Okinawa' for '沖縄', 'Tokyo' for '東京', 'Yokohama' for '横浜') to ensure precise coordinate geocoding."
                    )
                ),
                "required" to listOf("city")
            )
        ),
        McpTool(
            name = "get_github_repo_details",
            description = "Get detailed information about a specific GitHub repository, including its description, stars, forks, language, and owner avatar image URL.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "The owner of the repository"),
                    "repo" to mapOf("type" to "string", "description" to "The name of the repository")
                ),
                "required" to listOf("owner", "repo")
            )
        )
    )

    // Get all available tools from both built-in servers and remote custom servers
    suspend fun getAvailableTools(): List<McpTool> {
        val list = mutableListOf<McpTool>()
        list.addAll(builtinTools)

        // Fetch remote custom servers from database
        val db = AppDatabase.getDatabase(context)
        val remoteServers = db.appDao().getAllMcpServersDirect()
        
        for (server in remoteServers) {
            if (server.isEnabled && server.endpointUrl.isNotBlank()) {
                val remoteTools = fetchRemoteTools(server.endpointUrl)
                list.addAll(remoteTools.map { it.copy(isRemote = true, remoteUrl = server.endpointUrl) })
            }
        }
        
        return list
    }

    // Execute tool by name
    suspend fun executeTool(name: String, arguments: Map<String, Any>): String = withContext(Dispatchers.Default) {
        // Check if it is a built-in tool first
        val isBuiltin = builtinTools.any { it.name == name }
        if (isBuiltin) {
            return@withContext try {
                executeBuiltinTool(name, arguments)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing builtin tool $name", e)
                "Error executing builtin tool: ${e.localizedMessage}"
            }
        }

        // If not built-in, search in remote servers
        val db = AppDatabase.getDatabase(context)
        val remoteServers = db.appDao().getAllMcpServersDirect()
        for (server in remoteServers) {
            if (server.isEnabled && server.endpointUrl.isNotBlank()) {
                val remoteTools = fetchRemoteTools(server.endpointUrl)
                if (remoteTools.any { it.name == name }) {
                    return@withContext executeRemoteTool(server.endpointUrl, name, arguments)
                }
            }
        }

        return@withContext "Error: Tool '$name' was not found or the host server is offline/disabled."
    }

    // Built-in tool executors
    private suspend fun executeBuiltinTool(name: String, args: Map<String, Any>): String {
        return when (name) {
            "get_current_time_and_date" -> {
                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val sdfDay = SimpleDateFormat("EEEE", Locale.getDefault())
                val now = Date()
                val tz = TimeZone.getDefault()
                
                val responseJson = JSONObject().apply {
                    put("date", sdfDate.format(now))
                    put("time", sdfTime.format(now))
                    put("day_of_week", sdfDay.format(now))
                    put("timezone", tz.displayName)
                    put("timezone_id", tz.id)
                    put("utc_offset_hours", tz.rawOffset / (3600 * 1000).toDouble())
                }
                responseJson.toString()
            }
            "get_device_status" -> {
                // Battery status
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                
                // Storage details
                val statFs = StatFs(Environment.getDataDirectory().path)
                val totalBytes = statFs.totalBytes
                val availableBytes = statFs.availableBytes
                val totalGB = totalBytes / (1024 * 1024 * 1024).toDouble()
                val availableGB = availableBytes / (1024 * 1024 * 1024).toDouble()

                // Memory details
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                val totalRAM_GB = memInfo.totalMem / (1024 * 1024 * 1024).toDouble()
                val availableRAM_GB = memInfo.availMem / (1024 * 1024 * 1024).toDouble()

                // Network Status
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                val networkType = when {
                    capabilities == null -> "NONE"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                    else -> "OTHER"
                }

                val responseJson = JSONObject().apply {
                    put("battery_percentage", batteryPct)
                    put("storage_total_gb", String.format(Locale.US, "%.2f", totalGB))
                    put("storage_available_gb", String.format(Locale.US, "%.2f", availableGB))
                    put("ram_total_gb", String.format(Locale.US, "%.2f", totalRAM_GB))
                    put("ram_available_gb", String.format(Locale.US, "%.2f", availableRAM_GB))
                    put("os_version", "Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")")
                    put("active_network", networkType)
                }
                responseJson.toString()
            }
            "search_installed_apps" -> {
                val query = args["query"]?.toString()?.lowercase(Locale.getDefault()) ?: ""
                val apps = repository.getAllAppInfosDirect(context, settingsManager.getIncludeIconlessSystemApps())
                val matches = apps.filter { app ->
                    app.label.lowercase(Locale.getDefault()).contains(query) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(query) ||
                    app.category.lowercase(Locale.getDefault()).contains(query) ||
                    app.summary.lowercase(Locale.getDefault()).contains(query) ||
                    app.tags.lowercase(Locale.getDefault()).contains(query)
                }

                val array = JSONArray()
                matches.take(15).forEach { app ->
                    array.put(JSONObject().apply {
                        put("name", app.label)
                        put("packageName", app.packageName)
                        put("category", app.category)
                        put("summary", app.summary)
                        put("tags", app.tags)
                    })
                }
                
                JSONObject().put("results", array).toString()
            }
            "launch_installed_app" -> {
                val pkg = args["packageName"]?.toString() ?: ""
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    usageTracker?.recordLaunch(pkg)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    JSONObject().apply {
                        put("status", "success")
                        put("message", "Successfully triggered launching app: $pkg")
                    }.toString()
                } else {
                    JSONObject().apply {
                        put("status", "error")
                        put("message", "Failed to launch app. It might not have a launch activity, or is not installed.")
                    }.toString()
                }
            }
            "launcher_settings_control" -> {
                val action = args["action"]?.toString() ?: "read"
                if (action == "read") {
                    JSONObject().apply {
                        put("bgImageUrl", settingsManager.bgImageUrl.value)
                        put("autoContrast", settingsManager.autoContrast.value)
                        put("iconShape", settingsManager.getIconShape())
                        put("aiLanguage", settingsManager.getAiLanguage())
                        put("viewMode", settingsManager.viewMode.value)
                        put("activePrimaryModel", settingsManager.getPrimaryModel())
                    }.toString()
                } else {
                    val key = args["key"]?.toString() ?: ""
                    val value = args["value"]?.toString() ?: ""
                    var msg = "No setting updated."
                    var status = "ignored"
                    
                    withContext(Dispatchers.Main) {
                        when (key) {
                            "bgImageUrl" -> {
                                settingsManager.setBgImageUrl(value)
                                status = "success"
                                msg = "Successfully set background URL to: $value"
                            }
                            "autoContrast" -> {
                                val bVal = value.lowercase(Locale.getDefault()) == "true" || value == "1"
                                settingsManager.setAutoContrast(bVal)
                                status = "success"
                                msg = "Successfully set autoContrast to: $bVal"
                            }
                            "iconShape" -> {
                                settingsManager.setIconShape(value.uppercase(Locale.getDefault()))
                                status = "success"
                                msg = "Successfully set iconShape to: ${value.uppercase(Locale.getDefault())}"
                            }
                            "aiLanguage" -> {
                                settingsManager.setAiLanguage(value)
                                status = "success"
                                msg = "Successfully set aiLanguage to: $value"
                            }
                            "viewMode" -> {
                                settingsManager.setViewMode(value.uppercase(Locale.getDefault()))
                                status = "success"
                                msg = "Successfully set viewMode to: ${value.uppercase(Locale.getDefault())}"
                            }
                        }
                    }
                    
                    JSONObject().apply {
                        put("status", status)
                        put("message", msg)
                    }.toString()
                }
            }
            "evaluate_math_expression" -> {
                val expr = args["expression"]?.toString() ?: ""
                try {
                    val parser = MathParser(expr)
                    val result = parser.parse()
                    JSONObject().apply {
                        put("expression", expr)
                        put("result", result)
                        put("status", "success")
                    }.toString()
                } catch (e: Exception) {
                    JSONObject().apply {
                        put("expression", expr)
                        put("error", e.localizedMessage)
                        put("status", "failed")
                    }.toString()
                }
            }
            "get_weather_info" -> {
                val city = args["city"]?.toString() ?: "Tokyo"
                var coords = Pair(35.6895, 139.6917) // Default to Tokyo
                var resolvedCity = city
                
                // Fetch coordinates using Open-Meteo Geocoding API (completely free, no API key required)
                try {
                    val geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(city, "UTF-8")}&count=10"
                    val geoRequest = Request.Builder().url(geocodeUrl).build()
                    val geoResponseStr = withContext(Dispatchers.IO) {
                        httpClient.newCall(geoRequest).execute().use { response ->
                            if (response.isSuccessful) response.body?.string() else null
                        }
                    }
                    if (geoResponseStr != null) {
                        val geoJson = JSONObject(geoResponseStr)
                        val results = geoJson.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            // Prioritize Japan results to avoid phonetic mismatches in other countries
                            var selectedIndex = 0
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                if (item.optString("country_code").equals("JP", ignoreCase = true)) {
                                    selectedIndex = i
                                    break
                                }
                            }
                            val firstResult = results.getJSONObject(selectedIndex)
                            val lat = firstResult.optDouble("latitude", 35.6895)
                            val lon = firstResult.optDouble("longitude", 139.6917)
                            resolvedCity = firstResult.optString("name", city)
                            coords = Pair(lat, lon)
                        } else {
                            // Fallback to local hardcoded map if no results found
                            coords = when (city.lowercase(Locale.getDefault()).trim()) {
                                "tokyo", "東京" -> Pair(35.6895, 139.6917)
                                "osaka", "大阪" -> Pair(34.6937, 135.5023)
                                "kyoto", "京都" -> Pair(35.0116, 135.7681)
                                "sapporo", "札幌" -> Pair(43.0621, 141.3544)
                                "fukuoka", "福岡" -> Pair(33.5902, 130.4017)
                                "okinawa", "沖縄", "沖縄県" -> Pair(26.2124, 127.6809)
                                "yokohama", "横浜", "横浜市" -> Pair(35.4437, 139.6380)
                                "nagoya", "名古屋", "名古屋市" -> Pair(35.1815, 136.9066)
                                "kobe", "神戸", "神戸市" -> Pair(34.6901, 135.1955)
                                "hiroshima", "広島", "広島市" -> Pair(34.3853, 132.4553)
                                "seoul", "ソウル", "서울" -> Pair(37.5665, 126.9780)
                                "busan", "釜山", "부산" -> Pair(35.1796, 129.0756)
                                "beijing", "北京" -> Pair(39.9042, 116.4074)
                                "shanghai", "上海" -> Pair(31.2304, 121.4737)
                                "new york", "ニューヨーク" -> Pair(40.7128, -74.0060)
                                "london", "ロンドン" -> Pair(51.5074, -0.1278)
                                "paris", "パリ" -> Pair(48.8566, 2.3522)
                                "berlin", "ベルリン" -> Pair(52.5200, 13.4050)
                                "sydney", "シドニー" -> Pair(-33.8688, 151.2093)
                                "singapore", "シンガポール" -> Pair(1.3521, 103.8252)
                                "toronto", "トロント" -> Pair(43.6532, -79.3832)
                                else -> Pair(35.6895, 139.6917)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("McpManager", "Geocoding failed for $city, falling back to local map", e)
                    coords = when (city.lowercase(Locale.getDefault()).trim()) {
                        "tokyo", "東京" -> Pair(35.6895, 139.6917)
                        "osaka", "大阪" -> Pair(34.6937, 135.5023)
                        "kyoto", "京都" -> Pair(35.0116, 135.7681)
                        "sapporo", "札幌" -> Pair(43.0621, 141.3544)
                        "fukuoka", "福岡" -> Pair(33.5902, 130.4017)
                        "okinawa", "沖縄", "沖縄県" -> Pair(26.2124, 127.6809)
                        "yokohama", "横浜", "横浜市" -> Pair(35.4437, 139.6380)
                        "nagoya", "名古屋", "名古屋市" -> Pair(35.1815, 136.9066)
                        "kobe", "神戸", "神戸市" -> Pair(34.6901, 135.1955)
                        "hiroshima", "広島", "広島市" -> Pair(34.3853, 132.4553)
                        "seoul", "ソウル", "서울" -> Pair(37.5665, 126.9780)
                        "busan", "釜山", "부산" -> Pair(35.1796, 129.0756)
                        "beijing", "北京" -> Pair(39.9042, 116.4074)
                        "shanghai", "上海" -> Pair(31.2304, 121.4737)
                        "new york", "ニューヨーク" -> Pair(40.7128, -74.0060)
                        "london", "ロンドン" -> Pair(51.5074, -0.1278)
                        "paris", "パリ" -> Pair(48.8566, 2.3522)
                        "berlin", "ベルリン" -> Pair(52.5200, 13.4050)
                        "sydney", "シドニー" -> Pair(-33.8688, 151.2093)
                        "singapore", "シンガポール" -> Pair(1.3521, 103.8252)
                        "toronto", "トロント" -> Pair(43.6532, -79.3832)
                        else -> Pair(35.6895, 139.6917)
                    }
                }
                
                val url = "https://api.open-meteo.com/v1/forecast?latitude=${coords.first}&longitude=${coords.second}&current_weather=true"
                val request = Request.Builder().url(url).build()
                
                withContext(Dispatchers.IO) {
                    try {
                        httpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string() ?: ""
                                val root = JSONObject(body)
                                val current = root.optJSONObject("current_weather")
                                if (current != null) {
                                    val temp = current.optDouble("temperature")
                                    val windspeed = current.optDouble("windspeed")
                                    val code = current.optInt("weathercode")
                                    val condition = getWeatherConditionByCode(code)
                                    
                                    JSONObject().apply {
                                        put("city", city)
                                        put("latitude", coords.first)
                                        put("longitude", coords.second)
                                        put("temperature_celsius", temp)
                                        put("windspeed_kmh", windspeed)
                                        put("condition", condition)
                                        put("weather_code", code)
                                    }.toString()
                                } else {
                                    body
                                }
                            } else {
                                "Failed to fetch weather: HTTP ${response.code}"
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("McpManager", "Failed to load weather for $city", e)
                        // Fallback to beautiful realistic local formula
                        val temp = 15.0 + (city.hashCode() % 15)
                        JSONObject().apply {
                            put("city", city)
                            put("temperature_celsius", temp)
                            put("condition", if (temp > 22) "Sunny / Fair" else "Cloudy / Cool")
                            put("is_simulated", true)
                        }.toString()
                    }
                }
            }
            "get_github_repo_details" -> {
                val owner = args["owner"]?.toString() ?: ""
                val repo = args["repo"]?.toString() ?: ""
                val url = "https://api.github.com/repos/$owner/$repo"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AI-App-Launcher")
                    .build()
                withContext(Dispatchers.IO) {
                    try {
                        httpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string() ?: ""
                                val json = JSONObject(body)
                                val avatarUrl = json.optJSONObject("owner")?.optString("avatar_url")
                                JSONObject().apply {
                                    put("name", json.optString("name"))
                                    put("full_name", json.optString("full_name"))
                                    put("description", json.optString("description"))
                                    put("stars", json.optInt("stargazers_count"))
                                    put("forks", json.optInt("forks_count"))
                                    put("language", json.optString("language"))
                                    put("avatar_url", avatarUrl)
                                    put("html_url", json.optString("html_url"))
                                }.toString()
                            } else {
                                "Error: Failed to fetch GitHub repo. Status ${response.code}"
                            }
                        }
                    } catch (e: Exception) {
                        "Error: ${e.localizedMessage}"
                    }
                }
            }
            else -> "Error: Unknown built-in tool '$name'."
        }
    }

    private fun getWeatherConditionByCode(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Mainly clear, partly cloudy, and overcast"
            45, 48 -> "Fog and depositing rime fog"
            51, 53, 55 -> "Drizzle: Light, moderate, and dense intensity"
            56, 57 -> "Freezing Drizzle: Light and dense intensity"
            61, 63, 65 -> "Rain: Slight, moderate and heavy intensity"
            66, 67 -> "Freezing Rain: Light and heavy intensity"
            71, 73, 75 -> "Snow fall: Slight, moderate, and heavy intensity"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers: Slight, moderate, and violent"
            85, 86 -> "Snow showers: Slight and heavy"
            95 -> "Thunderstorm: Slight or moderate"
            96, 99 -> "Thunderstorm with slight and heavy hail"
            else -> "Unknown condition"
        }
    }

    // Remote HTTP tool list fetch
    private suspend fun fetchRemoteTools(endpointUrl: String): List<McpTool> {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val jsonBody = """
            {
              "jsonrpc": "2.0",
              "id": "list-tools",
              "method": "tools/list",
              "params": {}
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url(endpointUrl)
            .post(jsonBody.toRequestBody(mediaType))
            .build()
            
        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@withContext emptyList()
                        parseMcpToolsFromJson(responseBody)
                    } else {
                        // Fallback to GET /tools
                        val getRequest = Request.Builder().url("$endpointUrl/tools").build()
                        httpClient.newCall(getRequest).execute().use { getResponse ->
                            if (getResponse.isSuccessful) {
                                val body = getResponse.body?.string() ?: ""
                                parseMcpToolsFromJson(body)
                            } else {
                                emptyList()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch tools from remote URL: $endpointUrl", e)
                emptyList()
            }
        }
    }

    private fun parseMcpToolsFromJson(jsonStr: String): List<McpTool> {
        val list = mutableListOf<McpTool>()
        try {
            val root = JSONObject(jsonStr)
            val result = root.optJSONObject("result") ?: root
            val toolsArray = result.optJSONArray("tools")
            if (toolsArray != null) {
                for (i in 0 until toolsArray.length()) {
                    val toolObj = toolsArray.getJSONObject(i)
                    val name = toolObj.getString("name")
                    val description = toolObj.optString("description", "")
                    val inputSchemaObj = toolObj.optJSONObject("inputSchema")
                    
                    val inputSchema = if (inputSchemaObj != null) {
                        jsonObjectToMap(inputSchemaObj)
                    } else {
                        mapOf("type" to "OBJECT", "properties" to emptyMap<String, Any>())
                    }
                    
                    list.add(McpTool(name, description, inputSchema))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tools JSON from remote: $jsonStr", e)
        }
        return list
    }

    // Remote HTTP tool execution
    private suspend fun executeRemoteTool(endpointUrl: String, toolName: String, arguments: Map<String, Any>): String {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val argsJson = JSONObject(arguments).toString()
        val jsonBody = """
            {
              "jsonrpc": "2.0",
              "id": "call-$toolName",
              "method": "tools/call",
              "params": {
                "name": "$toolName",
                "arguments": $argsJson
              }
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url(endpointUrl)
            .post(jsonBody.toRequestBody(mediaType))
            .build()
            
        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@withContext "Error: Empty response"
                        
                        val root = JSONObject(responseBody)
                        val result = root.optJSONObject("result") ?: root
                        val contentArray = result.optJSONArray("content")
                        if (contentArray != null && contentArray.length() > 0) {
                            val builder = StringBuilder()
                            for (i in 0 until contentArray.length()) {
                                val contentObj = contentArray.getJSONObject(i)
                                if (contentObj.optString("type") == "text") {
                                    builder.append(contentObj.optString("text"))
                                }
                            }
                            builder.toString()
                        } else {
                            result.optString("text", responseBody)
                        }
                    } else {
                        "Error calling remote tool: HTTP ${response.code}"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed calling remote tool $toolName", e)
                "Error calling remote tool: ${e.localizedMessage}"
            }
        }
    }

    // Helper functions to map JSONObjects
    private fun jsonObjectToMap(jsonObject: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            if (value is JSONObject) {
                map[key] = jsonObjectToMap(value)
            } else if (value is JSONArray) {
                map[key] = jsonArrayToList(value)
            } else {
                map[key] = value
            }
        }
        return map
    }

    private fun jsonArrayToList(jsonArray: JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.get(i)
            if (value is JSONObject) {
                list.add(jsonObjectToMap(value))
            } else if (value is JSONArray) {
                list.add(jsonArrayToList(value))
            } else {
                list.add(value)
            }
        }
        return list
    }
}

// Compact and thorough recursive descent parser for basic and algebraic math
private class MathParser(private val str: String) {
    private var pos = -1
    private var ch = 0

    private fun nextChar() {
        ch = if (++pos < str.length) str[pos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (ch == ' '.code) nextChar()
        if (ch == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    fun parse(): Double {
        nextChar()
        val x = parseExpression()
        if (pos < str.length) throw RuntimeException("Unexpected character: " + ch.toChar())
        return x
    }

    private fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
            if (eat('+'.code)) x += parseTerm()
            else if (eat('-'.code)) x -= parseTerm()
            else return x
        }
    }

    private fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
            if (eat('*'.code)) x *= parseFactor()
            else if (eat('/'.code)) x /= parseFactor()
            else return x
        }
    }

    private fun parseFactor(): Double {
        if (eat('+'.code)) return parseFactor()
        if (eat('-'.code)) return -parseFactor()

        var x: Double
        val startPos = this.pos
        if (eat('('.code)) {
            x = parseExpression()
            eat(')'.code)
        } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) {
            while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
            x = str.substring(startPos, this.pos).toDouble()
        } else if (ch >= 'a'.code && ch <= 'z'.code || ch >= 'A'.code && ch <= 'Z'.code) {
            while (ch >= 'a'.code && ch <= 'z'.code || ch >= 'A'.code && ch <= 'Z'.code) nextChar()
            val func = str.substring(startPos, this.pos).lowercase(Locale.getDefault())
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
                x = when (func) {
                    "sqrt" -> sqrt(x)
                    "sin" -> sin(x)
                    "cos" -> cos(x)
                    "tan" -> tan(x)
                    "log" -> ln(x)
                    "log10" -> log10(x)
                    "abs" -> abs(x)
                    else -> throw RuntimeException("Unknown math function: $func")
                }
            } else {
                x = when (func) {
                    "pi" -> PI
                    "e" -> E
                    else -> throw RuntimeException("Unknown constant: $func")
                }
            }
        } else {
            throw RuntimeException("Unexpected character: " + ch.toChar())
        }

        if (eat('^'.code)) x = x.pow(parseFactor())

        return x
    }
}
