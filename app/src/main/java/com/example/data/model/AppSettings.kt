package com.example.data.model

enum class ReadAloudMode {
    AUTO_FALLBACK, // Tries native ChatGPT read aloud, falls back to Android TTS if audio doesn't start
    NATIVE_ONLY,   // Only uses ChatGPT's native read aloud button
    TTS_ONLY       // Instantly speaks extracted assistant text using Android high-def TTS
}

data class AppSettings(
    val speechLanguage: String = "de-DE",
    val readAloudMode: ReadAloudMode = ReadAloudMode.AUTO_FALLBACK,
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val loopDelaySeconds: Int = 1,
    val autoLaunchChatGpt: Boolean = true,
    val showFloatingHud: Boolean = true,
    val vibrateOnTransitions: Boolean = true,
    val chatGptPackage: String = "com.openai.chatgpt",
    val silenceTimeoutMs: Long = 1800L,
    val generationDebounceMs: Long = 1400L,
    val ttsFallbackTimeoutMs: Long = 4000L
)
