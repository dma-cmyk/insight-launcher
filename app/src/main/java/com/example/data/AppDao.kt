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
}
