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

    private val _favorites = MutableStateFlow<Set<String>>(loadAllFavorites())
    val favorites: StateFlow<Set<String>> = _favorites

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

    fun toggleFavorite(packageName: String) {
        val isFav = _favorites.value.contains(packageName)
        val newFav = !isFav
        
        prefs.edit()
            .putBoolean("fav_$packageName", newFav)
            .apply()

        val updatedFavorites = _favorites.value.toMutableSet()
        if (newFav) {
            updatedFavorites.add(packageName)
        } else {
            updatedFavorites.remove(packageName)
        }
        _favorites.value = updatedFavorites
    }

    fun isFavorite(packageName: String): Boolean {
        return _favorites.value.contains(packageName)
    }

    fun clearStats() {
        prefs.edit().clear().apply()
        _lastLaunchTimes.value = emptyMap()
        _launchCounts.value = emptyMap()
        _favorites.value = emptySet()
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

    private fun loadAllFavorites(): Set<String> {
        val set = mutableSetOf<String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("fav_") && value is Boolean && value) {
                set.add(key.substringAfter("fav_"))
            }
        }
        return set
    }
}
