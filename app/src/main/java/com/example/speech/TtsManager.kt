package com.example.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class TtsManager(
    private val context: Context,
    private val onSpeechStarted: () -> Unit = {},
    private val onSpeechCompleted: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentUtteranceId: String? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.GERMAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TtsManager", "German language missing or not supported, falling back to default locale")
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == currentUtteranceId) {
                        onSpeechStarted()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == currentUtteranceId) {
                        onSpeechCompleted()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == currentUtteranceId) {
                        onError("TTS Fehler bei der Sprachausgabe")
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == currentUtteranceId) {
                        onError("TTS Fehlercode: $errorCode")
                    }
                }
            })
            isInitialized = true
        } else {
            onError("TTS Initialisierungsfehler (Status $status)")
        }
    }

    fun speak(text: String, speed: Float = 1.0f, pitch: Float = 1.0f) {
        if (!isInitialized || tts == null) {
            // If TTS is not yet ready, wait briefly and complete
            onError("TTS nicht initialisiert")
            onSpeechCompleted()
            return
        }

        val cleanedText = cleanTextForSpeech(text)
        if (cleanedText.isBlank()) {
            onSpeechCompleted()
            return
        }

        tts?.setSpeechRate(speed)
        tts?.setPitch(pitch)

        val utteranceId = UUID.randomUUID().toString()
        currentUtteranceId = utteranceId

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
        currentUtteranceId = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        fun cleanTextForSpeech(input: String): String {
            return input
                // Remove code blocks
                .replace(Regex("```[\\s\\S]*?```"), "Codeblock übersprungen.")
                // Remove inline code
                .replace(Regex("`([^`]+)`"), "$1")
                // Remove markdown links [text](url) -> text
                .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
                // Remove citation marks like 【4:0†source】
                .replace(Regex("【[^】]+】"), "")
                // Remove markdown bold/italic asterisks & underscores
                .replace(Regex("[*_~#]"), "")
                // Clean up multiple spaces and empty lines
                .replace(Regex("\\n{2,}"), "\n")
                .trim()
        }
    }
}
