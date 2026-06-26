package com.example.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val tools: List<GeminiTool>? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiBlob? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null
)

@JsonClass(generateAdapter = true)
data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionCall(
    val name: String,
    val args: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionResponse(
    val name: String,
    val response: Map<String, Any>
)

@JsonClass(generateAdapter = true)
data class GeminiBlob(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: Map<String, Any>? = null,
    val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?
)

@JsonClass(generateAdapter = true)
data class GeminiAppAnalysis(
    val category: String,
    val summary: String,
    val tags: List<String>,
    val relatedLinks: List<RelatedLink>
)

@JsonClass(generateAdapter = true)
data class GeminiCategoryMerge(
    val oldCategory: String,
    val newCategory: String
)

@JsonClass(generateAdapter = true)
data class GeminiCategoryMergeResponse(
    val merges: List<GeminiCategoryMerge>
)

@JsonClass(generateAdapter = true)
data class GeminiEmbeddingRequest(
    val content: GeminiContent,
    val model: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiEmbeddingResponse(
    val embedding: GeminiEmbeddingValues?
)

@JsonClass(generateAdapter = true)
data class GeminiEmbeddingValues(
    val values: List<Float>?
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1beta/models/{model}:embedContent")
    suspend fun embedContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiEmbeddingRequest
    ): GeminiEmbeddingResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun analyzeApp(
        appName: String,
        packageName: String,
        modelName: String,
        backupModelName: String,
        languageCode: String = "ja",
        customApiKey: String? = null,
        userContextText: String? = null,
        fileName: String? = null,
        fileMimeType: String? = null,
        fileBytes: ByteArray? = null
    ): GeminiAppAnalysis? {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or default placeholder!")
            return null
        }

        val langName = when (languageCode) {
            "en" -> "English"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            else -> "Japanese"
        }

        val promptBuilder = StringBuilder()
        promptBuilder.append("""
            Please analyze the following Android application:
            - App Name: $appName
            - Package Name: $packageName
        """.trimIndent())

        if (!userContextText.isNullOrBlank()) {
            promptBuilder.append("\n\n- User Instructions/Corrections:\n$userContextText")
        }

        var isFileText = false
        var textFileContent = ""
        if (fileBytes != null && !fileMimeType.isNullOrBlank()) {
            val isImage = fileMimeType.startsWith("image/")
            if (!isImage) {
                // Read as text
                try {
                    textFileContent = String(fileBytes, Charsets.UTF_8)
                    isFileText = true
                    promptBuilder.append("\n\n- Reference Content from uploaded file ($fileName):\n$textFileContent")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read file bytes as UTF-8 string: ${e.message}")
                }
            }
        }

        promptBuilder.append("""
            
            
            Provide:
            1. An appropriate standard high-level category in $langName (e.g., "Productivity", "Social", "Utility", "Game", "Entertainment", "Finance", "Education", "System", or equivalent in $langName).
            2. A brief, useful summary of this application in $langName (explaining its core purpose). Use the provided User Instructions or Reference Content if available to guide and correct your understanding.
            3. At least 5 relevant tags or keywords in $langName.
            4. At least 3 relevant high-quality related links or external resources in $langName (e.g., official support site, Wikipedia article, Google Play Store search, documentation, or relevant guides).
            
            Format your response STRICTLY as a JSON object adhering to the schema.
        """.trimIndent())

        val schema: Map<String, Any> = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "category" to mapOf(
                    "type" to "STRING",
                    "description" to "A general standard category in $langName"
                ),
                "summary" to mapOf(
                    "type" to "STRING",
                    "description" to "A concise summary of the app's purpose in $langName"
                ),
                "tags" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf("type" to "STRING"),
                    "description" to "At least 5 relevant descriptive tags or keywords in $langName"
                ),
                "relatedLinks" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "title" to mapOf("type" to "STRING", "description" to "Title of the link in $langName"),
                            "url" to mapOf("type" to "STRING", "description" to "Valid URL related to the app, official support, Wikipedia, store page, or google search query")
                        ),
                        "required" to listOf("title", "url")
                    ),
                    "description" to "At least 3 high-quality relevant external links or resources in $langName"
                )
            ),
            "required" to listOf("category", "summary", "tags", "relatedLinks")
        )

        val parts = mutableListOf<GeminiPart>()
        parts.add(GeminiPart(text = promptBuilder.toString()))

        // If it's an image file, add it as a separate inlineData part
        if (fileBytes != null && !fileMimeType.isNullOrBlank() && fileMimeType.startsWith("image/")) {
            val base64Data = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
            parts.add(GeminiPart(inlineData = GeminiBlob(mimeType = fileMimeType, data = base64Data)))
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = parts)
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.2
            )
        )

        // Try primary model first, fallback to backup if it fails
        return try {
            Log.d(TAG, "Calling Gemini API using primary model: $modelName")
            val response = apiService.generateContent(modelName, apiKey, request)
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Primary model failed: ${e.message}. Retrying with backup model: $backupModelName", e)
            try {
                val response = apiService.generateContent(backupModelName, apiKey, request)
                parseResponse(response)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Backup model failed: ${fallbackEx.message}", fallbackEx)
                null
            }
        }
    }

    private fun parseResponse(response: GeminiResponse): GeminiAppAnalysis? {
        val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (rawJson.isNullOrBlank()) {
            Log.e(TAG, "Gemini returned empty response")
            return null
        }

        return try {
            Log.d(TAG, "Raw JSON response: $rawJson")
            val adapter = moshi.adapter(GeminiAppAnalysis::class.java)
            adapter.fromJson(rawJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON response: ${e.message}", e)
            null
        }
    }

    suspend fun getEmbedding(
        text: String,
        customApiKey: String? = null,
        modelName: String = "text-embedding-004"
    ): List<Float>? = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing for embedding!")
            return@withContext null
        }

        val cleanModelName = modelName.removePrefix("models/")
        val fullModelName = "models/$cleanModelName"
        val request = GeminiEmbeddingRequest(
            content = GeminiContent(parts = listOf(GeminiPart(text = text))),
            model = fullModelName
        )

        try {
            Log.d(TAG, "Fetching embedding vector for model: $cleanModelName")
            val response = apiService.embedContent(cleanModelName, apiKey, request)
            response.embedding?.values
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get embedding with $modelName: ${e.message}. Trying fallback to gemini-embedding-001", e)
            try {
                val fallbackModel = "gemini-embedding-001"
                val fallbackRequest = GeminiEmbeddingRequest(
                    content = GeminiContent(parts = listOf(GeminiPart(text = text))),
                    model = "models/$fallbackModel"
                )
                val response = apiService.embedContent(fallbackModel, apiKey, fallbackRequest)
                response.embedding?.values
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback embedding also failed: ${fallbackEx.message}", fallbackEx)
                null
            }
        }
    }

    suspend fun correctVoiceInput(
        spokenText: String,
        modelName: String,
        customApiKey: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext null
        }

        val cleanModelName = modelName.removePrefix("models/")
        
        val prompt = """
            You are an AI assistant that corrects speech recognition text. 
            The user may have used filler words (like 'ah', 'um', 'er', 'あー', 'えーと'), hesitated, or repeated words. 
            Please remove the filler words, correct obvious misrecognitions, and output ONLY the corrected clean text. 
            Keep the original language and meaning intact. Do not add any conversational replies or quotes.
            
            Original text:
            $spokenText
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(
                parts = listOf(GeminiPart(text = prompt))
            ))
        )

        return@withContext try {
            val response = apiService.generateContent(cleanModelName, apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Voice correction failed: ${e.message}", e)
            null
        }
    }

    suspend fun mergeCategories(
        categories: List<String>,
        modelName: String,
        customApiKey: String? = null,
        languageCode: String = "ja"
    ): Map<String, String>? = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext emptyMap()
        
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext null
        }

        val cleanModelName = modelName.removePrefix("models/")
        
        val langName = when (languageCode) {
            "en" -> "English"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            else -> "Japanese"
        }

        val prompt = """
            You are an AI assistant that organizes app categories.
            The user has provided a list of categories that might be fragmented or too specific.
            Your task is to group semantically similar categories into broader, standard categories in $langName (e.g., "Productivity", "Social", "Utility", "Game", "Entertainment", "Finance", "Education", "System", etc.).
            If a category is already good, keep it as is.
            Output a JSON list where each object has 'oldCategory' and 'newCategory'.
            
            Categories to process:
            ${categories.joinToString(", ")}
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(
                parts = listOf(GeminiPart(text = prompt))
            )),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "merges" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "oldCategory" to mapOf("type" to "string"),
                                    "newCategory" to mapOf("type" to "string")
                                ),
                                "required" to listOf("oldCategory", "newCategory")
                            )
                        )
                    ),
                    "required" to listOf("merges")
                )
            )
        )

        return@withContext try {
            val response = apiService.generateContent(cleanModelName, apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val adapter = moshi.adapter(GeminiCategoryMergeResponse::class.java)
                val mergeResponse = adapter.fromJson(jsonText)
                mergeResponse?.merges?.associate { it.oldCategory to it.newCategory }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Category merge failed: ${e.message}", e)
            null
        }
    }

    suspend fun askAssistant(
        userInstruction: String,
        apps: List<AppInfo>,
        wikiEntries: List<LlmWikiEntry>,
        modelName: String,
        customApiKey: String? = null,
        languageCode: String = "ja",
        mcpManager: McpManager? = null
    ): GeminiAssistantResponse? = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiAssistantResponse(
                headline = if (languageCode == "ja") "APIキーが設定されていません" else "API Key is missing",
                answer = if (languageCode == "ja") "設定からGemini APIキーを設定してください。" else "Please configure your Gemini API Key in Settings.",
                relevantPackages = emptyList(),
                suggestions = listOf(
                    if (languageCode == "ja") "設定を開く" else "Open Settings",
                    if (languageCode == "ja") "お気に入りアプリ" else "Favorites"
                )
            )
        }

        val cleanModelName = modelName.removePrefix("models/")
        val langName = when (languageCode) {
            "en" -> "English"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            else -> "Japanese"
        }

        // Construct simplified apps info to avoid exceeding token limit
        val appsJsonBuilder = StringBuilder("[")
        apps.take(60).forEachIndexed { i, app ->
            if (i > 0) appsJsonBuilder.append(",")
            appsJsonBuilder.append("""{"name":"${app.label}","pkg":"${app.packageName}","cat":"${app.category}","summary":"${app.summary}","tags":"${app.tags}"}""")
        }
        appsJsonBuilder.append("]")

        val wikiContext = if (wikiEntries.isNotEmpty()) {
            val builder = StringBuilder("\n--- LLM WIKI / AI MEMORIES (IMPORTANT FACTS, CONTEXT, AND PREFERENCES TO ALWAYS REMEMBER) ---\n")
            wikiEntries.forEach { entry ->
                builder.append("Title: ${entry.title}\nCategory: ${entry.category}\nContent: ${entry.content}\n\n")
            }
            builder.append("------------------------------------------------------------------------------------\n")
            builder.toString()
        } else {
            ""
        }

        val prompt = """
            You are a smart, friendly AI Assistant inside a modern Android launcher called AI App Launcher.
            
            $wikiContext
            
            The user gave you this instruction/query:
            "$userInstruction"

            Here is a list of the user's installed and analyzed applications:
            ${appsJsonBuilder.toString()}

            Your tasks:
            1. Analyze the user's instruction. If they are looking for specific apps, categories, features, or recommendations, search through their installed apps list.
            2. Produce a "headline" summarizing your recommendation or response (short, punchy, bold title, e.g., "🎯 効率化アプリのご提案！" or "💬 友達とつながるSNSツール").
            3. Produce an "answer" (detailed explanation in $langName. Be friendly and engaging. Support bullet points, paragraphs, and emojis. It will be displayed in a very large and beautiful format, so make sure it's highly readable and structured).
               If the user asks about facts or instructions you have saved in your LLM WIKI/memories (shown above), use that stored knowledge to answer accurately!
            4. Identify up to 6 "relevantPackages" (exact package names from the list) that are most relevant to the user's query so we can display them as clickable app cards. If the query is a general question (e.g., "tell me a joke"), this can be empty or list 1-2 most frequently used apps as helpful suggestions.
            5. Provide 3 "suggestions" (short follow-up queries or questions in $langName, e.g., "ゲームを探して", "SNSアプリはどれ？").
            6. If the user's query asks for or would highly benefit from apps they DO NOT have installed, recommend up to 4 high-quality relevant apps from the Play Store in the "recommendedStoreApps" list.
            7. If the user's query indicates they are looking for developer templates, open-source projects, Kotlin codebases, libraries, or GitHub projects, provide a relevant concise search keyword in "githubSearchQuery" so the app can perform a real-time live search against GitHub Search API.
            8. You have powerful Model Context Protocol (MCP) tools available. If the user asks about real-time device stats, launch an app, evaluate a mathematical formula, retrieve the current date/time, launcher settings, or get the weather for a city, use the corresponding tool rather than guessing or hallucinating! Always call the appropriate tool.

            Format your response STRICTLY as a JSON object adhering to the schema.
        """.trimIndent()

        val schema = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "headline" to mapOf("type" to "STRING", "description" to "A short, engaging title summarizing your response in $langName"),
                "answer" to mapOf("type" to "STRING", "description" to "Detailed formatted explanation or response in $langName. Support emojis, lists, and bold text."),
                "relevantPackages" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf("type" to "STRING"),
                    "description" to "Exact package names of the installed apps that match the query"
                ),
                "suggestions" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf("type" to "STRING"),
                    "description" to "3 short follow-up suggestions in $langName"
                ),
                "recommendedStoreApps" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "name" to mapOf("type" to "STRING", "description" to "Application name"),
                            "packageName" to mapOf("type" to "STRING", "description" to "Android package name (e.g. com.android.chrome)"),
                            "description" to mapOf("type" to "STRING", "description" to "Brief explanation of why this app is recommended"),
                            "playStoreUrl" to mapOf("type" to "STRING", "description" to "Play store direct URL or search URL"),
                            "category" to mapOf("type" to "STRING", "description" to "Category name (e.g. Utility, Social, Dev, Productivity)")
                        ),
                        "required" to listOf("name", "packageName", "description", "playStoreUrl")
                    ),
                    "description" to "List of high-quality app recommendations that are NOT currently installed, suggesting the user to fetch them from Play Store"
                ),
                "githubSearchQuery" to mapOf(
                    "type" to "STRING",
                    "description" to "A search query keyword to look up real-time matching GitHub repositories (e.g. 'jetpack compose navigation' or 'android launcher'). Keep it short and precise. Set to null or empty string if GitHub search is not relevant."
                )
            ),
            "required" to listOf("headline", "answer", "relevantPackages", "suggestions")
        )

        // Prep tools
        val mcpTools = mcpManager?.getAvailableTools() ?: emptyList()
        val geminiTools = if (mcpTools.isNotEmpty()) {
            listOf(GeminiTool(functionDeclarations = mcpTools.map {
                GeminiFunctionDeclaration(
                    name = it.name,
                    description = it.description,
                    parameters = it.inputSchema
                )
            }))
        } else null

        val contents = mutableListOf(GeminiContent(
            role = "user",
            parts = listOf(GeminiPart(text = prompt))
        ))

        return@withContext try {
            var responseText: String? = null
            var loopCount = 0
            val maxLoops = 6

            while (loopCount < maxLoops) {
                val request = GeminiRequest(
                    contents = contents,
                    tools = geminiTools,
                    generationConfig = GeminiGenerationConfig(
                        responseMimeType = "application/json",
                        responseSchema = schema,
                        temperature = 0.4
                    )
                )

                val response = apiService.generateContent(cleanModelName, apiKey, request)
                val candidate = response.candidates?.firstOrNull()
                val candidateContent = candidate?.content
                val part = candidateContent?.parts?.firstOrNull()

                if (part == null) {
                    break
                }

                val functionCall = part.functionCall
                if (functionCall != null) {
                    Log.d(TAG, "Gemini requested function call: ${functionCall.name} with args: ${functionCall.args}")
                    
                    // Add the model turn with functionCall
                    contents.add(GeminiContent(
                        role = "model",
                        parts = listOf(part)
                    ))

                    // Execute MCP tool
                    val toolResult = try {
                        mcpManager?.executeTool(functionCall.name, functionCall.args ?: emptyMap()) ?: "Error: McpManager is offline"
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed executing MCP tool: ${functionCall.name}", e)
                        "Error: ${e.localizedMessage}"
                    }

                    Log.d(TAG, "MCP tool result: $toolResult")

                    // Add function response turn
                    contents.add(GeminiContent(
                        role = "function",
                        parts = listOf(GeminiPart(
                            functionResponse = GeminiFunctionResponse(
                                name = functionCall.name,
                                response = mapOf("content" to toolResult)
                            )
                        ))
                    ))

                    loopCount++
                } else {
                    responseText = part.text
                    break
                }
            }

            if (responseText != null) {
                val adapter = moshi.adapter(GeminiAssistantResponse::class.java)
                adapter.fromJson(responseText)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "askAssistant failed: ${e.message}", e)
            GeminiAssistantResponse(
                headline = if (languageCode == "ja") "エラーが発生しました" else "An error occurred",
                answer = if (languageCode == "ja") "AIの呼び出し中にエラーが発生しました: ${e.localizedMessage}" else "An error occurred while calling the AI: ${e.localizedMessage}",
                relevantPackages = emptyList(),
                suggestions = listOf(
                    if (languageCode == "ja") "もう一度試す" else "Try again",
                    if (languageCode == "ja") "お気に入りアプリ" else "Favorites"
                )
            )
        }
    }

    suspend fun extractWikiEntry(
        userPrompt: String,
        aiAnswer: String,
        modelName: String,
        customApiKey: String? = null,
        languageCode: String = "ja"
    ): LlmWikiEntry? = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext null

        val cleanModelName = modelName.removePrefix("models/")
        val langName = when (languageCode) {
            "en" -> "English"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            else -> "Japanese"
        }

        val prompt = """
            You are a memory consolidation module for an AI Assistant inside an Android launcher.
            Based on the following user query and AI response, extract a single important fact, custom instruction, user preference, or piece of knowledge that should be stored in the AI's long-term memory (LLM Wiki) so the AI won't forget it in future sessions.
            
            User query: "$userPrompt"
            AI response: "$aiAnswer"
            
            Your tasks:
            1. Formulate a short, descriptive title for this memory in $langName (e.g., "ユーザーはRPGゲームが好き" or "AIは要約時に箇条書きを使うこと").
            2. Extract/summarize the core fact, preference, or instruction in $langName. Keep it concise, clear, and actionable.
            3. Choose an appropriate category (e.g., "Preference", "Instruction", "Fact", "General").
            
            Format your response STRICTLY as a JSON object adhering to the schema.
        """.trimIndent()

        val schema = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "title" to mapOf("type" to "STRING", "description" to "Descriptive title of the memory in $langName"),
                "content" to mapOf("type" to "STRING", "description" to "Concise fact, preference, or instruction in $langName"),
                "category" to mapOf("type" to "STRING", "description" to "Category of the memory (e.g., Preference, Instruction, Fact)")
            ),
            "required" to listOf("title", "content", "category")
        )

        val request = GeminiRequest(
            contents = listOf(GeminiContent(
                parts = listOf(GeminiPart(text = prompt))
            )),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.3
            )
        )

        try {
            val response = apiService.generateContent(cleanModelName, apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val json = org.json.JSONObject(jsonText)
                LlmWikiEntry(
                    title = json.optString("title", "Saved Memory"),
                    content = json.optString("content", ""),
                    category = json.optString("category", "General")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractWikiEntry failed: ${e.message}", e)
            null
        }
    }
}

@JsonClass(generateAdapter = true)
data class RecommendedStoreApp(
    val name: String,
    val packageName: String,
    val description: String,
    val playStoreUrl: String,
    val category: String = "General"
)

@JsonClass(generateAdapter = true)
data class GeminiAssistantResponse(
    val headline: String,
    val answer: String,
    val relevantPackages: List<String>?,
    val suggestions: List<String>?,
    val recommendedStoreApps: List<RecommendedStoreApp>? = null,
    val githubSearchQuery: String? = null
)
