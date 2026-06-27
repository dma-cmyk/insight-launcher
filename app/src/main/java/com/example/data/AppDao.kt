package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM app_info_table ORDER BY label ASC")
    fun getAllAppsFlow(): Flow<List<AppInfo>>

    @Query("SELECT * FROM app_info_table WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppByPackageName(packageName: String): AppInfo?

    @Query("SELECT * FROM app_info_table")
    suspend fun getAllAppsDirect(): List<AppInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(appInfo: AppInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(appInfos: List<AppInfo>)

    @Query("UPDATE app_info_table SET category = :newCategory WHERE category = :oldCategory")
    suspend fun updateCategory(oldCategory: String, newCategory: String)

    @Query("SELECT DISTINCT category FROM app_info_table WHERE summary != '' AND category != ''")
    suspend fun getDistinctCategories(): List<String>

    @Query("DELETE FROM app_info_table WHERE packageName = :packageName")
    suspend fun deleteAppByPackageName(packageName: String)

    @Query("DELETE FROM app_info_table")
    suspend fun clearAllApps()

    @Query("UPDATE app_info_table SET category = '', summary = '', tags = '', relatedLinks = '', embedding = NULL")
    suspend fun resetAllAnalysis()

    // LLM Wiki (AI Memory) operations
    @Query("SELECT * FROM llm_wiki_table ORDER BY lastUpdated DESC")
    fun getAllWikiEntriesFlow(): Flow<List<LlmWikiEntry>>

    @Query("SELECT * FROM llm_wiki_table ORDER BY lastUpdated DESC")
    suspend fun getAllWikiEntriesDirect(): List<LlmWikiEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWikiEntry(entry: LlmWikiEntry)

    @Query("DELETE FROM llm_wiki_table WHERE id = :id")
    suspend fun deleteWikiEntryById(id: Long)

    @Query("DELETE FROM llm_wiki_table")
    suspend fun clearAllWikiEntries()

    // MCP Server operations
    @Query("SELECT * FROM mcp_servers_table ORDER BY lastUpdated DESC")
    fun getAllMcpServersFlow(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers_table ORDER BY lastUpdated DESC")
    suspend fun getAllMcpServersDirect(): List<McpServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMcpServer(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers_table WHERE id = :id")
    suspend fun deleteMcpServerById(id: Long)
}
