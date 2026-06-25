package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_info_table")
data class AppInfo(
    @PrimaryKey val packageName: String,
    val label: String,
    val category: String,
    val summary: String,
    val tags: String, // Comma-separated or JSON list of tags (minimum 5 tags)
    val relatedLinks: String, // JSON string representing list of RelatedLink objects (minimum 3 links)
    val isSystemApp: Boolean,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class RelatedLink(
    val title: String,
    val url: String
)

fun AppInfo.getParsedLinks(): List<RelatedLink> {
    if (relatedLinks.isBlank()) return emptyList()
    return try {
        val array = org.json.JSONArray(relatedLinks)
        val list = mutableListOf<RelatedLink>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(RelatedLink(obj.getString("title"), obj.getString("url")))
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

fun AppInfo.getParsedTags(): List<String> {
    if (tags.isBlank()) return emptyList()
    return tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
