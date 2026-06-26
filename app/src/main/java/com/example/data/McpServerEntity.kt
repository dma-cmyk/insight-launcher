package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "mcp_servers_table")
@JsonClass(generateAdapter = true)
data class McpServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val type: String, // "BUILTIN" or "REMOTE"
    val endpointUrl: String = "", // HTTP URL for remote
    val isEnabled: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)
