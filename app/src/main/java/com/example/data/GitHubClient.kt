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
import java.util.concurrent.TimeUnit

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
}
