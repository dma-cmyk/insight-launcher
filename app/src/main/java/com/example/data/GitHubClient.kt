package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class GitHubSearchResponse(
    @Json(name = "items") val items: List<GitHubRepo> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GitHubRepo(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "description") val description: String?,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "stargazers_count") val stargazersCount: Int,
    @Json(name = "owner") val owner: GitHubOwner
)

@JsonClass(generateAdapter = true)
data class GitHubOwner(
    @Json(name = "login") val login: String,
    @Json(name = "avatar_url") val avatarUrl: String?
)

data class GitHubRepoDetails(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val htmlUrl: String,
    val stargazersCount: Int,
    val ownerLogin: String,
    val ownerAvatarUrl: String?,
    val summaryExplanation: String, // Gemini-generated Markdown explanation
    val readmeContent: String // Raw README text or first part of it
)

interface GitHubService {
    @Headers("User-Agent: AI-App-Launcher-Applet")
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 5
    ): GitHubSearchResponse
}

object GitHubClient {
    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GitHubService = retrofit.create(GitHubService::class.java)

    suspend fun fetchRepoDetails(
        repo: GitHubRepo,
        targetLanguageCode: String,
        modelName: String,
        customApiKey: String? = null
    ): GitHubRepoDetails = withContext(Dispatchers.IO) {
        val owner = repo.owner.login
        val repoName = repo.name
        
        // Try to fetch README from main, then master
        val branches = listOf("main", "master")
        var readmeText = ""
        
        for (branch in branches) {
            val url = URL("https://raw.githubusercontent.com/$owner/$repoName/$branch/README.md")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AI-App-Launcher")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            try {
                if (connection.responseCode in 200..299) {
                    readmeText = connection.inputStream.bufferedReader().use { it.readText() }
                    break
                }
            } catch (e: Exception) {
                // Ignore and try next branch
            } finally {
                connection.disconnect()
            }
        }
        
        // If we still don't have a README, let's try a backup with lowercase readme.md
        if (readmeText.isBlank()) {
            val url = URL("https://raw.githubusercontent.com/$owner/$repoName/main/readme.md")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AI-App-Launcher")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            try {
                if (connection.responseCode in 200..299) {
                    readmeText = connection.inputStream.bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                connection.disconnect()
            }
        }

        val displayReadme = if (readmeText.isNotBlank()) readmeText else "No README file found or accessible for this repository."
        
        // Call Gemini to explain the repo in detail
        val explanation = GeminiClient.summarizeGitHubRepo(
            repoName = repo.fullName,
            description = repo.description,
            readmeContent = displayReadme.take(6000), // Avoid sending huge text
            targetLanguageCode = targetLanguageCode,
            modelName = modelName,
            customApiKey = customApiKey
        )

        GitHubRepoDetails(
            id = repo.id,
            name = repo.name,
            fullName = repo.fullName,
            description = repo.description,
            htmlUrl = repo.htmlUrl,
            stargazersCount = repo.stargazersCount,
            ownerLogin = repo.owner.login,
            ownerAvatarUrl = repo.owner.avatarUrl,
            summaryExplanation = explanation,
            readmeContent = displayReadme.take(2000)
        )
    }
}
