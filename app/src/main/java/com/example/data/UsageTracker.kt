package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UsageTracker(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_launcher_usage_stats", Context.MODE_PRIVATE)

    // StateFlows to emit live updates to the UI when an app is launched
    private val _lastLaunchTimes = MutableStateFlow<Map<String, Long>>(loadAllLastLaunchTimes())
    val lastLaunchTimes: StateFlow<Map<String, Long>> = _lastLaunchTimes

    private val _launchCounts = MutableStateFlow<Map<String, Int>>(loadAllLaunchCounts())
    val launchCounts: StateFlow<Map<String, Int>> = _launchCounts

    private val _favorites = MutableStateFlow<List<String>>(loadAllFavorites())
    val favorites: StateFlow<List<String>> = _favorites

    private val _customIcons = MutableStateFlow<Map<String, String>>(loadAllCustomIcons())
    val customIcons: StateFlow<Map<String, String>> = _customIcons

    fun recordLaunch(packageName: String) {
        val currentCount = prefs.getInt("count_$packageName", 0)
        val newCount = currentCount + 1
        val currentTime = System.currentTimeMillis()

        prefs.edit()
            .putInt("count_$packageName", newCount)
            .putLong("time_$packageName", currentTime)
            .apply()

        // Update the reactive flows
        val updatedTimes = _lastLaunchTimes.value.toMutableMap()
        updatedTimes[packageName] = currentTime
        _lastLaunchTimes.value = updatedTimes

        val updatedCounts = _launchCounts.value.toMutableMap()
        updatedCounts[packageName] = newCount
        _launchCounts.value = updatedCounts
    }

    fun getLaunchCount(packageName: String): Int {
        return prefs.getInt("count_$packageName", 0)
    }

    fun getLastLaunchTime(packageName: String): Long {
        return prefs.getLong("time_$packageName", 0L)
    }

    fun saveFavorites(newList: List<String>) {
        _favorites.value = newList
        val joined = newList.joinToString(",")
        prefs.edit()
            .putString("fav_order_list", joined)
            .apply()
    }

    fun toggleFavorite(packageName: String) {
        val currentList = _favorites.value.toMutableList()
        val isFav = currentList.contains(packageName)
        if (isFav) {
            currentList.remove(packageName)
            prefs.edit().putBoolean("fav_$packageName", false).apply()
        } else {
            currentList.add(packageName)
            prefs.edit().putBoolean("fav_$packageName", true).apply()
        }
        saveFavorites(currentList)
    }

    fun isFavorite(packageName: String): Boolean {
        return _favorites.value.contains(packageName)
    }

    fun setCustomIcon(packageName: String, icon: String?) {
        if (icon == null) {
            prefs.edit().remove("custom_icon_$packageName").apply()
        } else {
            prefs.edit().putString("custom_icon_$packageName", icon).apply()
        }
        val updated = _customIcons.value.toMutableMap()
        if (icon == null) {
            updated.remove(packageName)
        } else {
            updated[packageName] = icon
        }
        _customIcons.value = updated
    }

    fun clearStats() {
        prefs.edit().clear().apply()
        _lastLaunchTimes.value = emptyMap()
        _launchCounts.value = emptyMap()
        _favorites.value = emptyList()
    }

    private fun loadAllLastLaunchTimes(): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("time_") && value is Long) {
                map[key.substringAfter("time_")] = value
            }
        }
        return map
    }

    private fun loadAllLaunchCounts(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("count_") && value is Int) {
                map[key.substringAfter("count_")] = value
            }
        }
        return map
    }

    private fun loadAllFavorites(): List<String> {
        val orderedStr = prefs.getString("fav_order_list", null)
        if (orderedStr != null) {
            return if (orderedStr.isEmpty()) emptyList() else orderedStr.split(",")
        }
        val list = mutableListOf<String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("fav_") && value is Boolean && value) {
                list.add(key.substringAfter("fav_"))
            }
        }
        return list
    }

    private fun loadAllCustomIcons(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("custom_icon_") && value is String) {
                map[key.substringAfter("custom_icon_")] = value
            }
        }
        return map
    }
}
