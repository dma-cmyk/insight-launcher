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

data class AppToAnalyze(
    val packageName: String,
    val label: String
)

@JsonClass(generateAdapter = true)
data class GeminiBulkAppAnalysisItem(
    val packageName: String,
    val category: String,
    val summary: String,
    val tags: List<String>,
    val relatedLinks: List<RelatedLink>
)

@JsonClass(generateAdapter = true)
data class GeminiBulkAnalysisResponse(
    val results: List<GeminiBulkAppAnalysisItem>
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

    private fun escapeJsonNewlines(json: String): String {
        val sb = StringBuilder()
        var inString = false
        var isEscaped = false
        for (i in 0 until json.length) {
            val char = json[i]
            if (char == '"' && !isEscaped) {
                inString = !inString
            }
            if (char == '\\' && !isEscaped) {
                isEscaped = true
            } else {
                isEscaped = false
            }
            
            if (char == '\n' && inString) {
                sb.append("\\n")
            } else if (char == '\r' && inString) {
                sb.append("\\r")
            } else {
                sb.append(char)
            }
        }
        return sb.toString()
    }

    private fun cleanJsonText(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.trim()
        
        var jsonOnly = cleaned
        // 1. First, check if there's a markdown code block like ```json ... ``` or ``` ... ```
        val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```")
        val matchResult = codeBlockRegex.find(cleaned)
        if (matchResult != null) {
            val content = matchResult.groupValues[1].trim()
            if (content.startsWith("{") && content.endsWith("}")) {
                jsonOnly = content
            }
        } else {
            // 2. If no code block found, or if it didn't contain valid braces, find the outer-most curly braces
            val firstBrace = cleaned.indexOf('{')
            val lastBrace = cleaned.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
                jsonOnly = cleaned.substring(firstBrace, lastBrace + 1).trim()
            }
        }
        
        return escapeJsonNewlines(jsonOnly)
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
        fileBytes: ByteArray? = null,
        onModelSelected: ((String) -> Unit)? = null
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

        val categoryExamples = when (languageCode) {
            "en" -> "\"Productivity\", \"Social\", \"Utility\", \"Game\", \"Entertainment\""
            "ko" -> "\"생산성\", \"소셜\", \"유틸리티\", \"게임\", \"엔터테인먼트\""
            "zh" -> "\"生产力\", \"社交\", \"实用工具\", \"游戏\", \"娱乐\""
            else -> "\"生産性\", \"ソーシャル\", \"ユーティリティ\", \"ゲーム\", \"エンターテイメント\""
        }

        val cleanModelName = modelName.removePrefix("models/")
        val backupModelNameClean = backupModelName.removePrefix("models/")
        val isGemma = cleanModelName.contains("gemma", ignoreCase = true)
        val isBackupGemma = backupModelNameClean.contains("gemma", ignoreCase = true)

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
            1. An appropriate standard high-level category in $langName (MUST BE IN $langName, e.g., $categoryExamples).
            2. A brief, useful summary of this application in $langName (explaining its core purpose). Use the provided User Instructions or Reference Content if available to guide and correct your understanding.
            3. At least 5 relevant tags or keywords in $langName.
            4. At least 3 relevant high-quality related links or external resources in $langName (e.g., official support site, Wikipedia article, Google Play Store search, documentation, or relevant guides).
            
            Format your response STRICTLY as a JSON object adhering to the schema.
        """.trimIndent())

        if (isGemma || isBackupGemma) {
            promptBuilder.append("\n\nIMPORTANT: You must return ONLY a raw JSON string matching the expected JSON format. Do not wrap the JSON in markdown code blocks like ```json ... ```, and do not write any conversation before or after the JSON.")
        }

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
            onModelSelected?.invoke(cleanModelName)
            val primaryRequest = if (isGemma) {
                request.copy(
                    generationConfig = request.generationConfig?.copy(
                        responseMimeType = null,
                        responseSchema = null
                    )
                )
            } else request
            val response = apiService.generateContent(cleanModelName, apiKey, primaryRequest)
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Primary model failed: ${e.message}. Retrying with backup model: $backupModelName", e)
            try {
                onModelSelected?.invoke(backupModelNameClean)
                val backupRequest = if (isBackupGemma) {
                    request.copy(
                        generationConfig = request.generationConfig?.copy(
                            responseMimeType = null,
                            responseSchema = null
                        )
                    )
                } else request
                val response = apiService.generateContent(backupModelNameClean, apiKey, backupRequest)
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
            val cleanedJson = cleanJsonText(rawJson) ?: ""
            Log.d(TAG, "Parsed JSON response: $cleanedJson")
            val adapter = moshi.adapter(GeminiAppAnalysis::class.java).lenient()
            adapter.fromJson(cleanedJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON response: ${e.message}", e)
            null
        }
    }

    suspend fun analyzeAppsBulk(
        apps: List<AppToAnalyze>,
        modelName: String,
        backupModelName: String,
        languageCode: String = "ja",
        customApiKey: String? = null,
        userContextText: String? = null,
        onModelSelected: ((String) -> Unit)? = null
    ): List<GeminiBulkAppAnalysisItem>? {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or default placeholder!")
            return null
        }
        if (apps.isEmpty()) return emptyList()

        val langName = when (languageCode) {
            "en" -> "English"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            else -> "Japanese"
        }

        val categoryExamples = when (languageCode) {
            "en" -> "\"Productivity\", \"Social\", \"Utility\", \"Game\", \"Entertainment\""
            "ko" -> "\"생산성\", \"소셜\", \"유틸리티\", \"게임\", \"엔터테인먼트\""
            "zh" -> "\"生产力\", \"社交\", \"实用工具\", \"游戏\", \"娱乐\""
            else -> "\"生産性\", \"ソーシャル\", \"ユーティリティ\", \"ゲーム\", \"エンターテイメント\""
        }

        val cleanModelName = modelName.removePrefix("models/")
        val backupModelNameClean = backupModelName.removePrefix("models/")
        val isGemma = cleanModelName.contains("gemma", ignoreCase = true)
        val isBackupGemma = backupModelNameClean.contains("gemma", ignoreCase = true)

        val promptBuilder = StringBuilder()
        promptBuilder.append("Please analyze the following list of Android applications in bulk:\n")
        apps.forEachIndexed { index, app ->
            promptBuilder.append("${index + 1}. App Name: ${app.label}, Package Name: ${app.packageName}\n")
        }

        if (!userContextText.isNullOrBlank()) {
            promptBuilder.append("\n- User Instructions/Corrections for Categorization & Analysis:\n$userContextText\n")
        }

        promptBuilder.append("""
            
            Provide the following information for EACH of the applications in the list:
            1. The exact same packageName (must match the input exactly).
            2. An appropriate standard high-level category in $langName (MUST BE IN $langName, e.g., $categoryExamples). If user instructions are provided, use them to guide your categorization.
            3. A brief, useful summary of this application in $langName (explaining its core purpose).
            4. At least 5 relevant tags or keywords in $langName.
            5. At least 3 relevant high-quality related links or external resources in $langName (e.g., official support site, Wikipedia article, Google Play Store search, documentation, or relevant guides).
            
            Format your response STRICTLY as a JSON object matching the responseSchema. It must have a top-level "results" array where each item contains the fields: "packageName", "category", "summary", "tags", "relatedLinks".
        """.trimIndent())

        if (isGemma || isBackupGemma) {
            promptBuilder.append("\n\nIMPORTANT: You must return ONLY a raw JSON string matching the expected JSON format. Do not wrap the JSON in markdown code blocks like ```json ... ```, and do not write any conversation before or after the JSON.")
        }

        val schema: Map<String, Any> = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "results" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "packageName" to mapOf("type" to "STRING", "description" to "The exact package name of the app"),
                            "category" to mapOf("type" to "STRING", "description" to "A general standard category in $langName"),
                            "summary" to mapOf("type" to "STRING", "description" to "A concise summary of the app's purpose in $langName"),
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
                                        "url" to mapOf("type" to "STRING", "description" to "Valid URL related to the app")
                                    ),
                                    "required" to listOf("title", "url")
                                ),
                                "description" to "At least 3 high-quality relevant external links or resources in $langName"
                            )
                        ),
                        "required" to listOf("packageName", "category", "summary", "tags", "relatedLinks")
                    )
                )
            ),
            "required" to listOf("results")
        )

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = promptBuilder.toString())))
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.2
            )
        )

        return try {
            Log.d(TAG, "Calling Gemini API for bulk analysis using primary model: $modelName")
            onModelSelected?.invoke(cleanModelName)
            val primaryRequest = if (isGemma) {
                request.copy(
                    generationConfig = request.generationConfig?.copy(
                        responseMimeType = null,
                        responseSchema = null
                    )
                )
            } else request
            val response = apiService.generateContent(cleanModelName, apiKey, primaryRequest)
            parseBulkResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Primary model bulk analysis failed: ${e.message}. Retrying with backup model: $backupModelName", e)
            try {
                onModelSelected?.invoke(backupModelNameClean)
                val backupRequest = if (isBackupGemma) {
                    request.copy(
                        generationConfig = request.generationConfig?.copy(
                            responseMimeType = null,
                            responseSchema = null
                        )
                    )
                } else request
                val response = apiService.generateContent(backupModelNameClean, apiKey, backupRequest)
                parseBulkResponse(response)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Backup model bulk analysis failed: ${fallbackEx.message}", fallbackEx)
                null
            }
        }
    }

    private fun parseBulkResponse(response: GeminiResponse): List<GeminiBulkAppAnalysisItem>? {
        val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (rawJson.isNullOrBlank()) {
            Log.e(TAG, "Gemini bulk returned empty response")
            return null
        }

        return try {
            val cleanedJson = cleanJsonText(rawJson) ?: ""
            Log.d(TAG, "Parsed bulk JSON response: $cleanedJson")
            val adapter = moshi.adapter(GeminiBulkAnalysisResponse::class.java).lenient()
            val parsed = adapter.fromJson(cleanedJson)
            parsed?.results
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse bulk JSON response: ${e.message}", e)
            null
        }
    }

    suspend fun getEmbedding(
        text: String,
        customApiKey: String? = null,
        modelName: String = "gemini-embedding-001"
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
            Sometimes the speech recognizer returns romaji or misrecognized characters instead of proper words (e.g. 'githubderanntya-apurisagasitekite').
            Please remove the filler words, correct obvious misrecognitions, convert romaji to proper Japanese if applicable, and output ONLY the corrected clean text. 
            Keep the original language and meaning intact. Do not add any conversational replies, tags, or quotes.
            
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
        val isGemma = cleanModelName.contains("gemma", ignoreCase = true)
        
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
            ${if (isGemma) "\n\nIMPORTANT: Return ONLY a raw JSON string of format {\"merges\":[{\"oldCategory\":\"...\",\"newCategory\":\"...\"}]}. Do not include markdown blocks or extra text." else ""}
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(
                parts = listOf(GeminiPart(text = prompt))
            )),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = if (isGemma) null else "application/json",
                responseSchema = if (isGemma) null else mapOf(
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
            val cleanedJson = cleanJsonText(jsonText)
            if (cleanedJson != null) {
                val adapter = moshi.adapter(GeminiCategoryMergeResponse::class.java).lenient()
                val mergeResponse = adapter.fromJson(cleanedJson)
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
        val isGemma = cleanModelName.contains("gemma", ignoreCase = true)
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
                builder.append("Title: ${entry.title}\nCategory: ${entry.category}\nContent: ${entry.content}\nTags: ${entry.tags.joinToString(", ")}\n\n")
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
            6. If the user's query asks for or would highly benefit from apps they DO NOT have installed, recommend up to 4 high-quality relevant apps from the Play Store in the "recommendedStoreApps" list. The descriptions of these apps must be written in $langName.
            7. If the user's query indicates they are looking for developer templates, open-source projects, Kotlin codebases, libraries, or GitHub projects, provide a relevant concise search keyword in "githubSearchQuery" so the app can perform a real-time live search against GitHub Search API.
            8. You have powerful Model Context Protocol (MCP) tools available. If the user asks about real-time device stats, launch an app, evaluate a mathematical formula, retrieve the current date/time, launcher settings, get the weather for a city, or fetch detailed info/images about a specific GitHub repo or Play Store app, use the corresponding tool rather than guessing or hallucinating! Always call the appropriate tool. If the tool returns an image URL (e.g., avatar_url or icon_url), output it in the 'headerImageUrl' field.

            Format your response STRICTLY as a JSON object adhering to the schema.
        """.trimIndent()

        val finalPrompt = if (isGemma) {
            prompt + "\n\nIMPORTANT: You must return ONLY a raw JSON string matching the expected JSON format. Do not wrap the JSON in markdown code blocks like ```json ... ```, and do not write any conversation before or after the JSON."
        } else {
            prompt
        }

        val schema = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "headline" to mapOf("type" to "STRING", "description" to "A short, engaging title summarizing your response in $langName"),
                "answer" to mapOf("type" to "STRING", "description" to "Detailed formatted explanation or response in $langName. Support emojis, lists, and bold text."),
                "headerImageUrl" to mapOf(
                    "type" to "STRING",
                    "nullable" to true,
                    "description" to "URL of an image to display at the top of the response (e.g. GitHub avatar, app icon). Extract this from MCP tool results if available. Set to null if no image is available."
                ),
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
                    "nullable" to true,
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "name" to mapOf("type" to "STRING", "description" to "Application name"),
                            "packageName" to mapOf("type" to "STRING", "description" to "Android package name (e.g. com.android.chrome)"),
                            "description" to mapOf("type" to "STRING", "description" to "Brief explanation of why this app is recommended, written in $langName"),
                            "playStoreUrl" to mapOf("type" to "STRING", "description" to "Play store direct URL or search URL"),
                            "category" to mapOf("type" to "STRING", "description" to "Category name (e.g. Utility, Social, Dev, Productivity)"),
                            "iconUrl" to mapOf("type" to "STRING", "description" to "A high-quality icon URL of the app. It MUST be a valid image URL. If unknown, omit.", "nullable" to true)
                        ),
                        "required" to listOf("name", "packageName", "description", "playStoreUrl")
                    ),
                    "description" to "List of high-quality app recommendations that are NOT currently installed, suggesting the user to fetch them from Play Store"
                ),
                "githubSearchQuery" to mapOf(
                    "type" to "STRING",
                    "nullable" to true,
                    "description" to "A search query keyword to look up real-time matching GitHub repositories (e.g. 'jetpack compose navigation' or 'android launcher'). Keep it short and precise. Set to null or empty string if GitHub search is not relevant."
                )
            ),
            "required" to listOf("headline", "answer", "relevantPackages", "suggestions")
        )

        // Prep tools (disable MCP tools for Gemma as it doesn't support tools/function calling over standard payload)
        val mcpTools = if (isGemma) emptyList() else (mcpManager?.getAvailableTools() ?: emptyList())
        val geminiTools = if (mcpTools.isNotEmpty()) {
            listOf(GeminiTool(functionDeclarations = mcpTools.map {
                val params = it.inputSchema
                val properties = params["properties"] as? Map<*, *>
                val cleanedParams = if (properties.isNullOrEmpty()) null else params
                
                GeminiFunctionDeclaration(
                    name = it.name,
                    description = it.description,
                    parameters = cleanedParams
                )
            }))
        } else null

        val contents = mutableListOf(GeminiContent(
            role = "user",
            parts = listOf(GeminiPart(text = finalPrompt))
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
                        responseMimeType = if (geminiTools == null && !isGemma) "application/json" else null,
                        responseSchema = if (geminiTools == null && !isGemma) schema else null,
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

                    val resultMap = mapOf("result" to toolResult)

                    // Add function response turn
                    contents.add(GeminiContent(
                        role = "function",
                        parts = listOf(GeminiPart(
                            functionResponse = GeminiFunctionResponse(
                                name = functionCall.name,
                                response = resultMap
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
                val cleanedJson = cleanJsonText(responseText) ?: ""
                Log.d(TAG, "Parsed chat JSON response: $cleanedJson")
                val adapter = moshi.adapter(GeminiAssistantResponse::class.java).lenient()
                adapter.fromJson(cleanedJson)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "askAssistant failed: ${e.message}", e)
            val isRateLimit = e is retrofit2.HttpException && e.code() == 429
            val headline = if (isRateLimit) {
                if (languageCode == "ja") "⚠️ リクエスト制限に達しました (HTTP 429)" else "⚠️ Rate Limit Exceeded (HTTP 429)"
            } else {
                if (languageCode == "ja") "エラーが発生しました" else "An error occurred"
            }
            val answer = if (isRateLimit) {
                if (languageCode == "ja") {
                    "現在、Gemini APIの無料枠のリクエスト制限（HTTP 429 Too Many Requests）に達しています。\n\n" +
                    "**【主な原因】**\n" +
                    "無料プランのAPIキーには、1分間に送信できるリクエスト回数（通常10〜15回程度）の厳しい制限があります。特にアプリの一括自動解析を行った直後や、MCPツール連携による連続呼び出しが発生した場合に一時的な制限にかかりやすくなります。\n\n" +
                    "**【解決方法】**\n" +
                    "1. **少し時間をおく**: 1分〜数分ほどお待ちいただくと、制限が自動的に解除されます。\n" +
                    "2. **専用のAPIキーを設定する**: ご自身のGoogle AI Studio（無料枠あり）でAPIキーを発行し、このアプリの「設定」画面から設定していただくことで、制限を大幅に緩和できます。"
                } else {
                    "You have reached the temporary rate limit for the free tier of the Gemini API (HTTP 429 Too Many Requests).\n\n" +
                    "**【Why?】**\n" +
                    "The free tier is limited to a certain number of requests per minute (typically 10-15 RPM). Bulk app auto-analysis, or rapid sequential tool calls (MCP), can trigger this limit.\n\n" +
                    "**【Solutions】**\n" +
                    "1. **Wait a bit**: Please wait for about a minute and try again.\n" +
                    "2. **Use your own API Key**: Head over to the Settings screen in this app and input your personal Gemini API Key from Google AI Studio to increase your usage limits."
                }
            } else {
                if (languageCode == "ja") "AIの呼び出し中にエラーが発生しました: ${e.localizedMessage}" else "An error occurred while calling the AI: ${e.localizedMessage}"
            }
            
            GeminiAssistantResponse(
                headline = headline,
                answer = answer,
                relevantPackages = emptyList(),
                suggestions = listOf(
                    if (languageCode == "ja") "もう一度試す" else "Try again",
                    if (languageCode == "ja") "設定を開く" else "Open Settings"
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
        val isGemma = cleanModelName.contains("gemma", ignoreCase = true)
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
            4. Provide exactly 5 relevant tags (keywords) related to this memory in $langName.
            
            Format your response STRICTLY as a JSON object adhering to the schema.
            ${if (isGemma) "\nIMPORTANT: Return ONLY a raw JSON string of format {\"title\":\"...\",\"content\":\"...\",\"category\":\"...\", \"tags\":[\"...\", ...], \"relatedLinkIds\":[]}. Do not include markdown block tags or extra text." else ""}
        """.trimIndent()

        val schema = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "title" to mapOf("type" to "STRING", "description" to "Descriptive title of the memory in $langName"),
                "content" to mapOf("type" to "STRING", "description" to "Concise fact, preference, or instruction in $langName"),
                "category" to mapOf("type" to "STRING", "description" to "Category of the memory (e.g., Preference, Instruction, Fact)"),
                "tags" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf("type" to "STRING"),
                    "description" to "List of exactly 5 relevant tags/keywords in $langName"
                )
            ),
            "required" to listOf("title", "content", "category", "tags")
        )

        val request = GeminiRequest(
            contents = listOf(GeminiContent(
                parts = listOf(GeminiPart(text = prompt))
            )),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = if (isGemma) null else "application/json",
                responseSchema = if (isGemma) null else schema,
                temperature = 0.3
            )
        )

        try {
            val response = apiService.generateContent(cleanModelName, apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            val cleanedJson = cleanJsonText(jsonText)
            if (cleanedJson != null) {
                val json = org.json.JSONObject(cleanedJson)
                val tagsArray = json.optJSONArray("tags")
                val tagsList = mutableListOf<String>()
                if (tagsArray != null) {
                    for (i in 0 until tagsArray.length()) {
                        tagsList.add(tagsArray.optString(i))
                    }
                }
                LlmWikiEntry(
                    title = json.optString("title", "Saved Memory"),
                    content = json.optString("content", ""),
                    category = json.optString("category", "General"),
                    tags = tagsList
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractWikiEntry failed: ${e.message}", e)
            null
        }
    }

    suspend fun autoLinkWikis(
        wikis: List<LlmWikiEntry>,
        modelName: String,
        customApiKey: String? = null
    ): List<LlmWikiEntry> = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || wikis.size < 2) return@withContext wikis

        val cleanModelName = modelName.removePrefix("models/")
        val isGemma = cleanModelName.startsWith("gemma")

        val wikisJson = org.json.JSONArray()
        wikis.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("title", it.title)
            obj.put("category", it.category)
            obj.put("tags", org.json.JSONArray(it.tags))
            wikisJson.put(obj)
        }

        val prompt = """
            You are an AI tasked with finding connections between memory cards (Wiki Entries).
            I will provide a JSON array of entries. Each entry has an 'id', 'title', 'category', and 'tags'.
            
            Your task is to link EACH entry to at least 3 other related entries (if there are enough total entries).
            You should find meaningful connections based on category, tags, or implied topics.
            
            Input JSON:
            ${wikisJson.toString()}
            
            Format your response STRICTLY as a JSON object adhering to the schema.
            The response MUST contain an array named 'linkedEntries'.
            Each item in 'linkedEntries' MUST have:
            - "id": the ID of the entry (number)
            - "relatedLinkIds": an array of related IDs (numbers). Aim for at least 3 related IDs per entry.
            
            ${if (isGemma) "\nIMPORTANT: Return ONLY a raw JSON string. Do not include markdown block tags." else ""}
        """.trimIndent()

        val schema = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "linkedEntries" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "id" to mapOf("type" to "INTEGER"),
                            "relatedLinkIds" to mapOf(
                                "type" to "ARRAY",
                                "items" to mapOf("type" to "INTEGER")
                            )
                        ),
                        "required" to listOf("id", "relatedLinkIds")
                    )
                )
            ),
            "required" to listOf("linkedEntries")
        )

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = if (isGemma) null else "application/json",
                responseSchema = if (isGemma) null else schema,
                temperature = 0.2
            )
        )

        try {
            val response = apiService.generateContent(cleanModelName, apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleanedJson = cleanJsonText(jsonText)
                if (cleanedJson != null) {
                    val jsonObj = org.json.JSONObject(cleanedJson)
                    val linkedArray = jsonObj.optJSONArray("linkedEntries")
                    if (linkedArray != null) {
                        val linksMap = mutableMapOf<Long, List<Long>>()
                        for (i in 0 until linkedArray.length()) {
                            val item = linkedArray.optJSONObject(i)
                            if (item != null) {
                                val id = item.optLong("id", -1L)
                                val relArray = item.optJSONArray("relatedLinkIds")
                                if (id != -1L && relArray != null) {
                                    val relList = mutableListOf<Long>()
                                    for (j in 0 until relArray.length()) {
                                        relList.add(relArray.optLong(j))
                                    }
                                    linksMap[id] = relList.filter { it != id }.distinct()
                                }
                            }
                        }
                        
                        return@withContext wikis.map { entry ->
                            val aiLinks = linksMap[entry.id] ?: emptyList()
                            // Merge existing and new, keeping unique
                            val mergedLinks = (entry.relatedLinkIds + aiLinks).distinct().filter { it != entry.id }
                            entry.copy(relatedLinkIds = mergedLinks, lastUpdated = System.currentTimeMillis())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "autoLinkWikis failed: ${e.message}", e)
        }
        return@withContext wikis
    }

    suspend fun bulkOrganizeWikis(
        wikis: List<LlmWikiEntry>,
        modelName: String,
        customApiKey: String? = null
    ): List<LlmWikiEntry> = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || wikis.isEmpty()) return@withContext wikis

        val cleanModelName = modelName.removePrefix("models/")
        val isGemma = cleanModelName.startsWith("gemma")

        val wikisJson = org.json.JSONArray()
        wikis.forEach {
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("title", it.title)
            obj.put("category", it.category)
            obj.put("tags", org.json.JSONArray(it.tags))
            wikisJson.put(obj)
        }

        val prompt = """
            You are an AI tasked with bulk-organizing memory cards (Wiki Entries).
            I will provide a JSON array of entries. Each entry has an 'id', 'title', 'category', and 'tags'.
            
            Your task is to:
            1. Standardize and consolidate the 'category' names (e.g., merge similar categories like "Development", "Dev", "Coding" into one standardized category).
            2. Clean up and standardize the 'tags' (remove duplicates, merge synonyms, keeping them concise and relevant).
            
            Input JSON:
            ${wikisJson.toString()}
            
            Format your response STRICTLY as a JSON object adhering to the schema.
            The response MUST contain an array named 'organizedEntries'.
            Each item in 'organizedEntries' MUST have:
            - "id": the ID of the entry (number)
            - "category": the standardized category (string)
            - "tags": an array of standardized tags (strings)
            
            ${if (isGemma) "\nIMPORTANT: Return ONLY a raw JSON string. Do not include markdown block tags." else ""}
        """.trimIndent()

        val schema = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "organizedEntries" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "id" to mapOf("type" to "INTEGER"),
                            "category" to mapOf("type" to "STRING"),
                            "tags" to mapOf(
                                "type" to "ARRAY",
                                "items" to mapOf("type" to "STRING")
                            )
                        ),
                        "required" to listOf("id", "category", "tags")
                    )
                )
            ),
            "required" to listOf("organizedEntries")
        )

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = if (isGemma) null else "application/json",
                responseSchema = if (isGemma) null else schema,
                temperature = 0.2
            )
        )

        try {
            val response = apiService.generateContent(cleanModelName, apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleanedJson = cleanJsonText(jsonText)
                if (cleanedJson != null) {
                    val jsonObj = org.json.JSONObject(cleanedJson)
                    val organizedArray = jsonObj.optJSONArray("organizedEntries")
                    if (organizedArray != null) {
                        val updatesMap = mutableMapOf<Long, Pair<String, List<String>>>()
                        for (i in 0 until organizedArray.length()) {
                            val item = organizedArray.optJSONObject(i)
                            if (item != null) {
                                val id = item.optLong("id", -1L)
                                val category = item.optString("category", "General")
                                val tagsArray = item.optJSONArray("tags")
                                if (id != -1L) {
                                    val tagsList = mutableListOf<String>()
                                    if (tagsArray != null) {
                                        for (j in 0 until tagsArray.length()) {
                                            tagsList.add(tagsArray.optString(j))
                                        }
                                    }
                                    updatesMap[id] = Pair(category, tagsList)
                                }
                            }
                        }
                        
                        return@withContext wikis.map { entry ->
                            val update = updatesMap[entry.id]
                            if (update != null) {
                                entry.copy(
                                    category = update.first,
                                    tags = update.second,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            } else {
                                entry
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "bulkOrganizeWikis failed: ${e.message}", e)
        }
        return@withContext wikis
    }

    suspend fun translateGitHubRepos(
        repos: List<com.example.data.GitHubRepo>,
        targetLanguageCode: String,
        modelName: String,
        customApiKey: String? = null
    ): List<com.example.data.GitHubRepo> = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || repos.isEmpty() || targetLanguageCode == "en") {
            return@withContext repos
        }
        val langName = when (targetLanguageCode) {
            "ja" -> "Japanese"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "zh" -> "Chinese"
            "hi" -> "Hindi"
            else -> "English"
        }
        if (langName == "English") return@withContext repos

        val cleanModelName = modelName.removePrefix("models/")
        val isGemma = cleanModelName.startsWith("gemma")
        
        val originalJsonArray = org.json.JSONArray()
        repos.forEachIndexed { index, repo ->
            if (!repo.description.isNullOrBlank()) {
                val obj = org.json.JSONObject()
                obj.put("index", index)
                obj.put("text", repo.description)
                originalJsonArray.put(obj)
            }
        }
        
        if (originalJsonArray.length() == 0) return@withContext repos

        val prompt = """
            Translate the following GitHub repository descriptions into $langName.
            Maintain the exact same 'index' for each item.
            
            Original texts:
            ${originalJsonArray.toString()}
            
            Format your response STRICTLY as a JSON object adhering to the schema.
            ${if (isGemma) "\nIMPORTANT: Return ONLY a raw JSON string. Do not include markdown block tags." else ""}
        """.trimIndent()

        val schema = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "translations" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "index" to mapOf("type" to "INTEGER"),
                            "translatedText" to mapOf("type" to "STRING")
                        ),
                        "required" to listOf("index", "translatedText")
                    )
                )
            ),
            "required" to listOf("translations")
        )

        val request = GeminiRequest(
            contents = listOf(GeminiContent(
                parts = listOf(GeminiPart(text = prompt))
            )),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = if (isGemma) null else "application/json",
                responseSchema = if (isGemma) null else schema,
                temperature = 0.3
            )
        )

        try {
            val response = apiService.generateContent(cleanModelName, apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            val cleanedJson = cleanJsonText(jsonText)
            
            if (cleanedJson != null) {
                val json = org.json.JSONObject(cleanedJson)
                val translationsArray = json.optJSONArray("translations")
                
                val translationMap = mutableMapOf<Int, String>()
                if (translationsArray != null) {
                    for (i in 0 until translationsArray.length()) {
                        val item = translationsArray.optJSONObject(i)
                        if (item != null) {
                            translationMap[item.optInt("index")] = item.optString("translatedText")
                        }
                    }
                }
                
                val translatedRepos = repos.mapIndexed { index, repo ->
                    if (translationMap.containsKey(index)) {
                        repo.copy(description = translationMap[index])
                    } else {
                        repo
                    }
                }
                return@withContext translatedRepos
            }
        } catch (e: Exception) {
            Log.e(TAG, "translateGitHubRepos failed: ${e.message}", e)
        }
        
        return@withContext repos
    }
}

@JsonClass(generateAdapter = true)
data class RecommendedStoreApp(
    val name: String,
    val packageName: String,
    val description: String,
    val playStoreUrl: String,
    val category: String = "General",
    val iconUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiAssistantResponse(
    val headline: String,
    val answer: String,
    val headerImageUrl: String? = null,
    val relevantPackages: List<String>?,
    val suggestions: List<String>?,
    val recommendedStoreApps: List<Any>? = null,
    val githubSearchQuery: String? = null
)
