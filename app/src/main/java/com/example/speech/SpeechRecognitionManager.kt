package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class SpeechRecognitionManager(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onReady() {}
        fun onSpeechStart() {}
        fun onRmsChanged(rmsdB: Float, normalized: Float) {}
        fun onPartialResult(interimText: String) {}
        fun onFinalResult(text: String)
        fun onError(code: Int, description: String)
        fun onEndOfSpeech() {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var languageTag: String = "de-DE"

    init {
        mainHandler.post {
            initRecognizer()
        }
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("SpeechRecognizer", "Spracherkennung ist auf diesem Gerät nicht verfügbar")
            listener.onError(-1, "Spracherkennung auf dem Gerät nicht verfügbar")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(InternalListener())
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Fehler bei der Initialisierung", e)
            listener.onError(-2, "Fehler beim Erstellen der Spracherkennung: ${e.message}")
        }
    }

    fun startListening(localeTag: String = "de-DE") {
        this.languageTag = localeTag
        mainHandler.post {
            if (speechRecognizer == null) {
                initRecognizer()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // Android 10 speech timeouts
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            }

            try {
                speechRecognizer?.cancel()
                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Log.e("SpeechRecognizer", "Fehler beim Starten des Listeners", e)
                listener.onError(-3, "Start fehlgeschlagen: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            if (isListening) {
                try {
                    speechRecognizer?.stopListening()
                } catch (e: Exception) {
                    Log.e("SpeechRecognizer", "Fehler beim Stoppen", e)
                }
                isListening = false
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e("SpeechRecognizer", "Fehler beim Abbrechen", e)
            }
            isListening = false
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e("SpeechRecognizer", "Fehler beim Beenden", e)
            }
            isListening = false
        }
    }

    private inner class InternalListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            listener.onReady()
        }

        override fun onBeginningOfSpeech() {
            listener.onSpeechStart()
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Normalizing rmsdB roughly from -2dB..10dB to 0..1 range
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            listener.onRmsChanged(rmsdB, normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            listener.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            isListening = false
            val description = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio-Aufnahmefehler"
                SpeechRecognizer.ERROR_CLIENT -> "Client-Fehler"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Fehlende Mikrofon-Berechtigung"
                SpeechRecognizer.ERROR_NETWORK -> "Netzwerkfehler"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netzwerk-Timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "Keine Spracheingabe erkannt"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Spracherkennung beschäftigt"
                SpeechRecognizer.ERROR_SERVER -> "Server-Fehler"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Zeitüberschreitung: Keine Sprache gehört"
                else -> "Unbekannter Fehler ($error)"
            }
            listener.onError(error, description)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim() ?: ""
            listener.onFinalResult(recognizedText)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val interim = matches?.firstOrNull()?.trim() ?: ""
            if (interim.isNotEmpty()) {
                listener.onPartialResult(interim)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
