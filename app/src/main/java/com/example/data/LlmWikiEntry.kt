package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "llm_wiki_table")
@JsonClass(generateAdapter = true)
data class LlmWikiEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "General",
    val tags: List<String> = emptyList(),
    val relatedLinkIds: List<Long> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)
