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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(appInfo: AppInfo)

    @Query("DELETE FROM app_info_table WHERE packageName = :packageName")
    suspend fun deleteAppByPackageName(packageName: String)

    @Query("DELETE FROM app_info_table")
    suspend fun clearAllApps()
}
