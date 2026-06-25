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
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiBlob? = null
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

        val fullModelName = if (modelName.startsWith("models/")) modelName else "models/$modelName"
        val request = GeminiEmbeddingRequest(
            content = GeminiContent(parts = listOf(GeminiPart(text = text))),
            model = fullModelName
        )

        try {
            Log.d(TAG, "Fetching embedding vector for model: $modelName")
            val response = apiService.embedContent(modelName, apiKey, request)
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

        val fullModelName = if (modelName.startsWith("models/")) modelName else "models/$modelName"
        
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

        try {
            val response = apiService.generateContent(fullModelName, apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Voice correction failed: ${e.message}", e)
            null
        }
    }
}
