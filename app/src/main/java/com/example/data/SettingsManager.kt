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
        const val KEY_AUTO_CONTRAST = "auto_contrast"
        const val KEY_INCLUDE_ICONLESS_SYSTEM_APPS = "include_iconless_system_apps"
        const val KEY_CUSTOM_CATEGORIZATION_PROMPT = "custom_categorization_prompt"

        const val DEFAULT_PRIMARY_MODEL = "gemini-flash-lite-latest"
        const val DEFAULT_BACKUP_MODEL = "gemini-flash-latest"
        const val DEFAULT_EMBEDDING_MODEL = "gemini-embedding-001"
        const val DEFAULT_BG_IMAGE = "procedural_nebula" // Canvas procedural nebula
        const val DEFAULT_AI_LANGUAGE = "en"
        const val DEFAULT_ICON_SHAPE = "ROUNDED_RECT"
        const val DEFAULT_INCLUDE_ICONLESS_SYSTEM_APPS = false
        const val DEFAULT_CUSTOM_CATEGORIZATION_PROMPT = ""

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

    private fun getInitialAiLanguage(): String {
        val existing = prefs.getString(KEY_AI_LANGUAGE, null)
        if (existing != null) return existing

        val sysLang = java.util.Locale.getDefault().language
        val detected = when {
            sysLang.startsWith("ja") -> "ja"
            sysLang.startsWith("ko") -> "ko"
            sysLang.startsWith("zh") -> "zh"
            sysLang.startsWith("en") -> "en"
            else -> DEFAULT_AI_LANGUAGE
        }
        prefs.edit().putString(KEY_AI_LANGUAGE, detected).apply()
        return detected
    }

    private val _aiLanguage = MutableStateFlow(getInitialAiLanguage())
    val aiLanguage: StateFlow<String> = _aiLanguage

    private val _iconShape = MutableStateFlow(prefs.getString(KEY_ICON_SHAPE, DEFAULT_ICON_SHAPE) ?: DEFAULT_ICON_SHAPE)
    val iconShape: StateFlow<String> = _iconShape

    private val _autoContrast = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CONTRAST, true))
    val autoContrast: StateFlow<Boolean> = _autoContrast

    private val _includeIconlessSystemApps = MutableStateFlow(prefs.getBoolean(KEY_INCLUDE_ICONLESS_SYSTEM_APPS, DEFAULT_INCLUDE_ICONLESS_SYSTEM_APPS))
    val includeIconlessSystemApps: StateFlow<Boolean> = _includeIconlessSystemApps

    private val _customCategorizationPrompt = MutableStateFlow(prefs.getString(KEY_CUSTOM_CATEGORIZATION_PROMPT, DEFAULT_CUSTOM_CATEGORIZATION_PROMPT) ?: DEFAULT_CUSTOM_CATEGORIZATION_PROMPT)
    val customCategorizationPrompt: StateFlow<String> = _customCategorizationPrompt

    fun getPrimaryModel(): String = prefs.getString(KEY_PRIMARY_MODEL, DEFAULT_PRIMARY_MODEL) ?: DEFAULT_PRIMARY_MODEL
    fun getBackupModel(): String = prefs.getString(KEY_BACKUP_MODEL, DEFAULT_BACKUP_MODEL) ?: DEFAULT_BACKUP_MODEL
    fun getEmbeddingModel(): String = prefs.getString(KEY_EMBEDDING_MODEL, DEFAULT_EMBEDDING_MODEL) ?: DEFAULT_EMBEDDING_MODEL
    fun getAiLanguage(): String = prefs.getString(KEY_AI_LANGUAGE, null) ?: getInitialAiLanguage()
    fun getGeminiApiKey(): String = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    fun getIconShape(): String = prefs.getString(KEY_ICON_SHAPE, DEFAULT_ICON_SHAPE) ?: DEFAULT_ICON_SHAPE
    fun getIncludeIconlessSystemApps(): Boolean = prefs.getBoolean(KEY_INCLUDE_ICONLESS_SYSTEM_APPS, DEFAULT_INCLUDE_ICONLESS_SYSTEM_APPS)
    fun getCustomCategorizationPrompt(): String = prefs.getString(KEY_CUSTOM_CATEGORIZATION_PROMPT, DEFAULT_CUSTOM_CATEGORIZATION_PROMPT) ?: DEFAULT_CUSTOM_CATEGORIZATION_PROMPT

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

    fun setAutoContrast(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CONTRAST, enabled).apply()
        _autoContrast.value = enabled
    }

    fun setIncludeIconlessSystemApps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_ICONLESS_SYSTEM_APPS, enabled).apply()
        _includeIconlessSystemApps.value = enabled
    }

    fun setCustomCategorizationPrompt(prompt: String) {
        prefs.edit().putString(KEY_CUSTOM_CATEGORIZATION_PROMPT, prompt).apply()
        _customCategorizationPrompt.value = prompt
    }
}
