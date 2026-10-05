package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppSettings
import com.example.data.model.ReadAloudMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voiceloop_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): AppSettings = _settingsFlow.value

    private fun loadSettings(): AppSettings {
        val lang = prefs.getString("speechLanguage", "de-DE") ?: "de-DE"
        val modeStr = prefs.getString("readAloudMode", ReadAloudMode.AUTO_FALLBACK.name) ?: ReadAloudMode.AUTO_FALLBACK.name
        val mode = try {
            ReadAloudMode.valueOf(modeStr)
        } catch (_: Exception) {
            ReadAloudMode.AUTO_FALLBACK
        }
        val speed = prefs.getFloat("ttsSpeed", 1.0f)
        val pitch = prefs.getFloat("ttsPitch", 1.0f)
        val loopDelay = prefs.getInt("loopDelaySeconds", 1)
        val autoLaunch = prefs.getBoolean("autoLaunchChatGpt", true)
        val showHud = prefs.getBoolean("showFloatingHud", true)
        val vibrate = prefs.getBoolean("vibrateOnTransitions", true)
        val pkg = prefs.getString("chatGptPackage", "com.openai.chatgpt") ?: "com.openai.chatgpt"
        val silence = prefs.getLong("silenceTimeoutMs", 1800L)
        val debounce = prefs.getLong("generationDebounceMs", 1400L)
        val fallback = prefs.getLong("ttsFallbackTimeoutMs", 4000L)

        return AppSettings(
            speechLanguage = lang,
            readAloudMode = mode,
            ttsSpeed = speed,
            ttsPitch = pitch,
            loopDelaySeconds = loopDelay,
            autoLaunchChatGpt = autoLaunch,
            showFloatingHud = showHud,
            vibrateOnTransitions = vibrate,
            chatGptPackage = pkg,
            silenceTimeoutMs = silence,
            generationDebounceMs = debounce,
            ttsFallbackTimeoutMs = fallback
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putString("speechLanguage", newSettings.speechLanguage)
            putString("readAloudMode", newSettings.readAloudMode.name)
            putFloat("ttsSpeed", newSettings.ttsSpeed)
            putFloat("ttsPitch", newSettings.ttsPitch)
            putInt("loopDelaySeconds", newSettings.loopDelaySeconds)
            putBoolean("autoLaunchChatGpt", newSettings.autoLaunchChatGpt)
            putBoolean("showFloatingHud", newSettings.showFloatingHud)
            putBoolean("vibrateOnTransitions", newSettings.vibrateOnTransitions)
            putString("chatGptPackage", newSettings.chatGptPackage)
            putLong("silenceTimeoutMs", newSettings.silenceTimeoutMs)
            putLong("generationDebounceMs", newSettings.generationDebounceMs)
            putLong("ttsFallbackTimeoutMs", newSettings.ttsFallbackTimeoutMs)
            apply()
        }
        _settingsFlow.value = newSettings
    }
}
