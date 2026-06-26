package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_launcher_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_PRIMARY_MODEL = "primary_model"
        const val KEY_BACKUP_MODEL = "backup_model"
        const val KEY_EMBEDDING_MODEL = "embedding_model"
        const val KEY_BG_IMAGE_URL = "bg_image_url"
        const val KEY_VIEW_MODE = "view_mode" // "GRID" or "LIST"
        const val KEY_AI_LANGUAGE = "ai_language"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_ICON_SHAPE = "icon_shape" // "ROUNDED_RECT", "CIRCLE", "SQUARE", "SQUIRCLE"

        const val DEFAULT_PRIMARY_MODEL = "gemini-flash-lite-latest"
        const val DEFAULT_BACKUP_MODEL = "gemini-flash-latest"
        const val DEFAULT_EMBEDDING_MODEL = "gemini-embedding-001"
        const val DEFAULT_BG_IMAGE = "procedural_nebula" // Canvas procedural nebula
        const val DEFAULT_AI_LANGUAGE = "ja"
        const val DEFAULT_ICON_SHAPE = "ROUNDED_RECT"

        val SPACE_PRESETS = listOf(
            PresetBg("Procedural Nebula (Offline/Battery-Save)", "procedural_nebula"),
            PresetBg("Carina Nebula", "https://images.unsplash.com/photo-1506318137071-a8e063b4bec0?q=80&w=1200"),
            PresetBg("Milky Way Space", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=1200"),
            PresetBg("Andromeda Galaxy", "https://images.unsplash.com/photo-1543722530-d2c3201371e7?q=80&w=1200"),
            PresetBg("Pleiades Cluster", "https://images.unsplash.com/photo-1518531933037-91b2f5f229cc?q=80&w=1200")
        )
    }

    data class PresetBg(val name: String, val value: String)

    private val _viewMode = MutableStateFlow(prefs.getString(KEY_VIEW_MODE, "GRID") ?: "GRID")
    val viewMode: StateFlow<String> = _viewMode

    private val _bgImageUrl = MutableStateFlow(prefs.getString(KEY_BG_IMAGE_URL, DEFAULT_BG_IMAGE) ?: DEFAULT_BG_IMAGE)
    val bgImageUrl: StateFlow<String> = _bgImageUrl

    private val _aiLanguage = MutableStateFlow(prefs.getString(KEY_AI_LANGUAGE, DEFAULT_AI_LANGUAGE) ?: DEFAULT_AI_LANGUAGE)
    val aiLanguage: StateFlow<String> = _aiLanguage

    private val _iconShape = MutableStateFlow(prefs.getString(KEY_ICON_SHAPE, DEFAULT_ICON_SHAPE) ?: DEFAULT_ICON_SHAPE)
    val iconShape: StateFlow<String> = _iconShape

    fun getPrimaryModel(): String = prefs.getString(KEY_PRIMARY_MODEL, DEFAULT_PRIMARY_MODEL) ?: DEFAULT_PRIMARY_MODEL
    fun getBackupModel(): String = prefs.getString(KEY_BACKUP_MODEL, DEFAULT_BACKUP_MODEL) ?: DEFAULT_BACKUP_MODEL
    fun getEmbeddingModel(): String = prefs.getString(KEY_EMBEDDING_MODEL, DEFAULT_EMBEDDING_MODEL) ?: DEFAULT_EMBEDDING_MODEL
    fun getAiLanguage(): String = prefs.getString(KEY_AI_LANGUAGE, DEFAULT_AI_LANGUAGE) ?: DEFAULT_AI_LANGUAGE
    fun getGeminiApiKey(): String = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    fun getIconShape(): String = prefs.getString(KEY_ICON_SHAPE, DEFAULT_ICON_SHAPE) ?: DEFAULT_ICON_SHAPE

    fun setPrimaryModel(model: String) {
        prefs.edit().putString(KEY_PRIMARY_MODEL, model).apply()
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key).apply()
    }

    fun setBackupModel(model: String) {
        prefs.edit().putString(KEY_BACKUP_MODEL, model).apply()
    }

    fun setEmbeddingModel(model: String) {
        prefs.edit().putString(KEY_EMBEDDING_MODEL, model).apply()
    }

    fun setBgImageUrl(url: String) {
        prefs.edit().putString(KEY_BG_IMAGE_URL, url).apply()
        _bgImageUrl.value = url
    }

    fun setViewMode(mode: String) {
        prefs.edit().putString(KEY_VIEW_MODE, mode).apply()
        _viewMode.value = mode
    }

    fun setAiLanguage(languageCode: String) {
        prefs.edit().putString(KEY_AI_LANGUAGE, languageCode).apply()
        _aiLanguage.value = languageCode
    }

    fun setIconShape(shape: String) {
        prefs.edit().putString(KEY_ICON_SHAPE, shape).apply()
        _iconShape.value = shape
    }
}
