package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class FDroidPackage(
    val packageName: String,
    val name: String,
    val summary: String,
    val iconUrl: String,
    val license: String
)

data class FDroidDetails(
    val packageName: String,
    val name: String,
    val summary: String,
    val iconUrl: String,
    val license: String,
    val summaryExplanation: String, // Gemini-generated Markdown explanation
    val apkDownloadLinks: List<String> // Extracted actual APK links
)

object FDroidClient {
    suspend fun search(query: String, page: Int = 1): List<FDroidPackage> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlString = if (page > 1) {
            "https://search.f-droid.org/?q=$encodedQuery&page=$page"
        } else {
            "https://search.f-droid.org/?q=$encodedQuery"
        }
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val results = mutableListOf<FDroidPackage>()
        try {
            if (connection.responseCode in 200..299) {
                val html = connection.inputStream.bufferedReader().use { it.readText() }

                // Regex to extract package information
                val blockRegex = """<a class="package-header" href="https://f-droid\.org/[^/]+/packages/([^/"]+)/?">.*?<img class="package-icon" src="([^"]+)".*?<h4 class="package-name">\s*(.*?)\s*</h4>.*?<span class="package-summary">(.*?)</span><span class="package-license">(.*?)</span>""".toRegex(RegexOption.DOT_MATCHES_ALL)

                val matches = blockRegex.findAll(html)
                for (match in matches) {
                    val packageName = match.groupValues[1]
                    val iconUrl = match.groupValues[2]
                    val name = match.groupValues[3].trim()
                    val summary = match.groupValues[4].trim()
                    val license = match.groupValues[5].trim()

                    results.add(FDroidPackage(packageName, name, summary, iconUrl, license))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }
        results
    }

    suspend fun fetchAppDetails(
        packageName: String,
        name: String,
        summary: String,
        iconUrl: String,
        license: String,
        targetLanguageCode: String,
        modelName: String,
        customApiKey: String? = null
    ): FDroidDetails = withContext(Dispatchers.IO) {
        val url = URL("https://f-droid.org/en/packages/$packageName/")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        var html = ""
        val apkLinks = mutableListOf<String>()
        try {
            if (connection.responseCode in 200..299) {
                html = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Extract APK links
                val apkRegex = """href="([^"]+\.apk)"""".toRegex()
                val matches = apkRegex.findAll(html)
                for (match in matches) {
                    var link = match.groupValues[1]
                    if (!link.startsWith("http")) {
                        link = "https://f-droid.org$link"
                    }
                    if (!apkLinks.contains(link)) {
                        apkLinks.add(link)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }

        // Clean HTML to text
        val cleanedText = if (html.isNotBlank()) cleanHtmlToText(html) else "Could not load F-Droid webpage content."
        
        // Summarize with Gemini
        val explanation = GeminiClient.summarizeFDroidApp(
            packageName = packageName,
            appName = name,
            summary = summary,
            htmlContent = cleanedText.take(6000), // Avoid sending huge text
            targetLanguageCode = targetLanguageCode,
            modelName = modelName,
            customApiKey = customApiKey
        )

        FDroidDetails(
            packageName = packageName,
            name = name,
            summary = summary,
            iconUrl = iconUrl,
            license = license,
            summaryExplanation = explanation,
            apkDownloadLinks = apkLinks.distinct().take(5) // Limit to top 5 direct APK download links
        )
    }

    private fun cleanHtmlToText(html: String): String {
        var text = html
        text = text.replace("""<script[^>]*>.*?</script>""".toRegex(RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace("""<style[^>]*>.*?</style>""".toRegex(RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace("""<header[^>]*>.*?</header>""".toRegex(RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace("""<footer[^>]*>.*?</footer>""".toRegex(RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace("""<nav[^>]*>.*?</nav>""".toRegex(RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace("<[^>]+>".toRegex(), " ")
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
        text = text.replace("""\s+""".toRegex(), " ").trim()
        return text
    }
}
