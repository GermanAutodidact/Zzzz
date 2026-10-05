package com.example.controller

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.data.model.AppSettings
import com.example.data.model.ConversationTurn
import com.example.data.model.LoopState
import com.example.data.model.LoopStep
import com.example.data.model.ReadAloudMode
import com.example.data.repository.VoiceLoopRepository
import com.example.service.VoiceLoopAccessibilityService
import com.example.speech.SpeechRecognitionManager
import com.example.speech.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceLoopController(
    private val context: Context,
    private val repository: VoiceLoopRepository,
    private val scope: CoroutineScope
) : VoiceLoopAccessibilityService.ChatGPTEventListener, SpeechRecognitionManager.Listener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _loopState = MutableStateFlow(LoopState())
    val loopState: StateFlow<LoopState> = _loopState.asStateFlow()

    private var speechManager: SpeechRecognitionManager? = null
    private var ttsManager: TtsManager? = null

    private var isLoopActive = false
    private var isPaused = false
    private var currentTurnStartTime = 0L
    private var lastUserPrompt = ""
    private var lastAssistantReply = ""
    private var audioMonitoringJob: Job? = null
    private var fallbackTimerRunnable: Runnable? = null
    private var isNativeAudioPlaying = false

    private val audioPlaybackCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>?) {
                if (!isLoopActive || isPaused) return
                val isPlaying = !configs.isNullOrEmpty() || audioManager.isMusicActive
                handleAudioPlaybackState(isPlaying)
            }
        }
    } else null

    init {
        VoiceLoopAccessibilityService.listener = this
        initSpeechAndTts()
    }

    private fun initSpeechAndTts() {
        speechManager = SpeechRecognitionManager(context, this)
        ttsManager = TtsManager(
            context = context,
            onSpeechStarted = {
                Log.d(TAG, "TTS Sprachausgabe gestartet")
            },
            onSpeechCompleted = {
                Log.d(TAG, "TTS Sprachausgabe beendet")
                onAudioFinished()
            },
            onError = { err ->
                Log.w(TAG, "TTS Fehler: $err")
                repository.log(TAG, "TTS Fehler: $err", "WARN")
                onAudioFinished()
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioPlaybackCallback != null) {
            try {
                audioManager.registerAudioPlaybackCallback(audioPlaybackCallback, mainHandler)
            } catch (e: Exception) {
                Log.w(TAG, "Konnte AudioPlaybackCallback nicht registrieren", e)
            }
        }
    }

    fun startLoop() {
        if (isLoopActive) return
        isLoopActive = true
        isPaused = false
        repository.log(TAG, "VoiceLoop gestartet", "INFO")
        transitionTo(LoopStep.LISTENING, "Starte Spracherkennung…")
        vibrate(short = true)

        val settings = repository.getSettings()
        if (settings.autoLaunchChatGpt) {
            launchChatGptApp()
        }

        startListeningInternal()
    }

    fun stopLoop() {
        if (!isLoopActive) return
        isLoopActive = false
        isPaused = false
        repository.log(TAG, "VoiceLoop gestoppt", "INFO")
        speechManager?.stopListening()
        ttsManager?.stop()
        cancelAudioMonitoring()
        transitionTo(LoopStep.IDLE, "Loop beendet")
        vibrate(short = false)
    }

    fun pauseLoop() {
        if (!isLoopActive || isPaused) return
        isPaused = true
        speechManager?.stopListening()
        ttsManager?.stop()
        cancelAudioMonitoring()
        transitionTo(LoopStep.PAUSED, "Pausiert durch Benutzer")
        vibrate(short = true)
    }

    fun resumeLoop() {
        if (!isLoopActive || !isPaused) return
        isPaused = false
        transitionTo(LoopStep.LISTENING, "Fortgesetzt…")
        vibrate(short = true)
        startListeningInternal()
    }

    fun restartListening() {
        if (!isLoopActive) {
            startLoop()
            return
        }
        isPaused = false
        ttsManager?.stop()
        cancelAudioMonitoring()
        transitionTo(LoopStep.LISTENING, "Höre neu zu…")
        vibrate(short = true)
        startListeningInternal()
    }

    private fun startListeningInternal() {
        val settings = repository.getSettings()
        speechManager?.startListening(settings.speechLanguage)
    }

    // --- Speech Recognition Callbacks ---

    override fun onReady() {
        if (!isLoopActive || isPaused) return
        transitionTo(LoopStep.LISTENING, "Bereit für Spracheingabe…")
    }

    override fun onSpeechStart() {
        if (!isLoopActive || isPaused) return
        transitionTo(LoopStep.LISTENING, "Sprache erkannt…")
    }

    override fun onRmsChanged(rmsdB: Float, normalized: Float) {
        if (_loopState.value.step == LoopStep.LISTENING) {
            _loopState.update { it.copy(audioVolumeLevel = normalized) }
        }
    }

    override fun onPartialResult(interimText: String) {
        if (!isLoopActive || isPaused) return
        _loopState.update {
            it.copy(
                currentTranscription = interimText,
                isPartialTranscription = true,
                statusDetail = "Erkenne: $interimText"
            )
        }
    }

    override fun onFinalResult(text: String) {
        if (!isLoopActive || isPaused) return

        if (text.isBlank()) {
            // No speech detected, retry listening after brief pause
            mainHandler.postDelayed({
                if (isLoopActive && !isPaused && _loopState.value.step == LoopStep.LISTENING) {
                    startListeningInternal()
                }
            }, 600)
            return
        }

        // Check for voice command "Pause" or "Stopp"
        val lower = text.lowercase()
        if (lower == "pause" || lower == "stopp" || lower == "stop" || lower == "abbrechen") {
            pauseLoop()
            return
        }

        currentTurnStartTime = System.currentTimeMillis()
        lastUserPrompt = text
        _loopState.update {
            it.copy(
                currentTranscription = text,
                isPartialTranscription = false,
                step = LoopStep.PROCESSING_SPEECH,
                statusDetail = "Spracheingabe abgeschlossen: \"$text\""
            )
        }
        repository.log(TAG, "Erkannter Text: \"$text\"", "INFO")
        vibrate(short = true)

        // Inject into ChatGPT
        injectAndSendToChatGPT(text)
    }

    override fun onError(code: Int, description: String) {
        if (!isLoopActive || isPaused) return

        // If simple timeout / no match, auto-restart listening
        if (code == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            code == android.speech.SpeechRecognizer.ERROR_NO_MATCH
        ) {
            mainHandler.postDelayed({
                if (isLoopActive && !isPaused && _loopState.value.step == LoopStep.LISTENING) {
                    startListeningInternal()
                }
            }, 500)
            return
        }

        repository.log(TAG, "Spracherkennungsfehler ($code): $description", "WARN")
        _loopState.update {
            it.copy(
                errorMessage = description,
                statusDetail = "Fehler: $description (Wiederhole…)"
            )
        }

        mainHandler.postDelayed({
            if (isLoopActive && !isPaused) {
                startListeningInternal()
            }
        }, 1500)
    }

    // --- ChatGPT Interaction & State ---

    private fun injectAndSendToChatGPT(text: String) {
        val service = VoiceLoopAccessibilityService.instance
        if (service == null) {
            val errMsg = "Bedienungshilfe-Dienst nicht aktiv. Bitte in den Einstellungen aktivieren."
            transitionTo(LoopStep.ERROR, errMsg)
            repository.log(TAG, errMsg, "ERROR")
            return
        }

        transitionTo(LoopStep.SENDING_TO_CHATGPT, "Sende an ChatGPT…")

        // Bring ChatGPT to foreground if not already visible
        launchChatGptApp()

        // Wait slightly for window to settle then send
        mainHandler.postDelayed({
            service.sendPrompt(text) { success, message ->
                if (success) {
                    transitionTo(LoopStep.WAITING_CHATGPT_REPLY, "Warte auf Antwort von ChatGPT…")
                    repository.log(TAG, "Prompt übergeben: $message", "INFO")
                } else {
                    repository.log(TAG, "Senden fehlgeschlagen: $message", "WARN")
                    _loopState.update { it.copy(statusDetail = message) }
                    // Retry once after brief delay
                    mainHandler.postDelayed({
                        service.sendPrompt(text) { retrySuccess, retryMsg ->
                            if (retrySuccess) {
                                transitionTo(LoopStep.WAITING_CHATGPT_REPLY, "Warte auf Antwort von ChatGPT…")
                            } else {
                                transitionTo(LoopStep.ERROR, "Senden fehlgeschlagen: $retryMsg")
                            }
                        }
                    }, 800)
                }
            }
        }, 500)
    }

    override fun onGenerationStarted() {
        if (!isLoopActive || isPaused) return
        transitionTo(LoopStep.WAITING_CHATGPT_REPLY, "ChatGPT tippt Antwort…")
    }

    override fun onGenerationProgress(currentText: String) {
        if (!isLoopActive || isPaused) return
        lastAssistantReply = currentText
        _loopState.update {
            it.copy(
                lastAssistantReply = currentText,
                statusDetail = "Generiert: ${currentText.takeLast(40)}"
            )
        }
    }

    override fun onGenerationCompleted(finalText: String) {
        if (!isLoopActive || isPaused) return
        lastAssistantReply = finalText
        repository.log(TAG, "ChatGPT Antwort vollständig (${finalText.length} Zeichen)", "INFO")
        transitionTo(LoopStep.READING_ALOUD, "Antwort fertig. Starte Vorlesen…")
        vibrate(short = true)

        executeReadAloud(finalText)
    }

    private fun executeReadAloud(replyText: String) {
        val settings = repository.getSettings()
        when (settings.readAloudMode) {
            ReadAloudMode.TTS_ONLY -> {
                speakWithTts(replyText)
            }
            ReadAloudMode.NATIVE_ONLY -> {
                triggerNativeReadAloud(replyText, fallbackToTts = false)
            }
            ReadAloudMode.AUTO_FALLBACK -> {
                triggerNativeReadAloud(replyText, fallbackToTts = true)
            }
        }
    }

    private fun triggerNativeReadAloud(replyText: String, fallbackToTts: Boolean) {
        val service = VoiceLoopAccessibilityService.instance
        if (service == null) {
            if (fallbackToTts) speakWithTts(replyText) else onAudioFinished()
            return
        }

        isNativeAudioPlaying = false
        service.triggerReadAloud { success, msg ->
            repository.log(TAG, "Native Read Aloud Trigger: $msg (Erfolg: $success)", "INFO")
            if (success) {
                startAudioMonitoring()
                if (fallbackToTts) {
                    scheduleFallbackTimer(replyText)
                }
            } else {
                if (fallbackToTts) {
                    repository.log(TAG, "Fallback auf Android TTS aktiviert", "INFO")
                    speakWithTts(replyText)
                } else {
                    onAudioFinished()
                }
            }
        }
    }

    private fun scheduleFallbackTimer(replyText: String) {
        fallbackTimerRunnable?.let { mainHandler.removeCallbacks(it) }
        val settings = repository.getSettings()
        fallbackTimerRunnable = Runnable {
            if (isLoopActive && !isPaused && _loopState.value.step == LoopStep.READING_ALOUD && !isNativeAudioPlaying) {
                repository.log(TAG, "Keine native Audio-Wiedergabe erkannt, aktiviere TTS-Fallback", "WARN")
                cancelAudioMonitoring()
                speakWithTts(replyText)
            }
        }
        mainHandler.postDelayed(fallbackTimerRunnable!!, settings.ttsFallbackTimeoutMs)
    }

    private fun speakWithTts(replyText: String) {
        val settings = repository.getSettings()
        cancelAudioMonitoring()
        _loopState.update { it.copy(statusDetail = "Lese Antwort vor…") }
        ttsManager?.speak(replyText, settings.ttsSpeed, settings.ttsPitch)
    }

    private fun startAudioMonitoring() {
        cancelAudioMonitoring()
        audioMonitoringJob = scope.launch(Dispatchers.IO) {
            var audioStarted = false
            var silenceCount = 0

            while (isLoopActive && !isPaused && _loopState.value.step == LoopStep.READING_ALOUD) {
                val isPlaying = audioManager.isMusicActive
                if (isPlaying) {
                    if (!audioStarted) {
                        audioStarted = true
                        isNativeAudioPlaying = true
                        fallbackTimerRunnable?.let { mainHandler.removeCallbacks(it) }
                        _loopState.update { it.copy(statusDetail = "ChatGPT liest vor…") }
                    }
                    silenceCount = 0
                } else if (audioStarted) {
                    silenceCount++
                    // If audio was playing and has now been silent for ~1.5s
                    if (silenceCount >= 3) {
                        mainHandler.post { onAudioFinished() }
                        break
                    }
                }
                delay(500)
            }
        }
    }

    private fun handleAudioPlaybackState(isPlaying: Boolean) {
        if (isPlaying && _loopState.value.step == LoopStep.READING_ALOUD) {
            isNativeAudioPlaying = true
            fallbackTimerRunnable?.let { mainHandler.removeCallbacks(it) }
            _loopState.update { it.copy(statusDetail = "ChatGPT liest vor…") }
        }
    }

    private fun cancelAudioMonitoring() {
        audioMonitoringJob?.cancel()
        audioMonitoringJob = null
        fallbackTimerRunnable?.let { mainHandler.removeCallbacks(it) }
        fallbackTimerRunnable = null
    }

    private fun onAudioFinished() {
        if (!isLoopActive || isPaused) return
        cancelAudioMonitoring()

        val duration = System.currentTimeMillis() - currentTurnStartTime
        val turn = ConversationTurn(
            userInputText = lastUserPrompt,
            chatGptResponseText = lastAssistantReply,
            durationMs = duration,
            status = "SUCCESS",
            mode = repository.getSettings().readAloudMode.name
        )
        scope.launch {
            repository.saveTurn(turn)
        }

        val settings = repository.getSettings()
        val delaySec = settings.loopDelaySeconds

        transitionTo(LoopStep.COOLDOWN, "Nächste Eingabe in ${delaySec}s…")
        _loopState.update { it.copy(turnCount = it.turnCount + 1) }

        mainHandler.postDelayed({
            if (isLoopActive && !isPaused) {
                transitionTo(LoopStep.LISTENING, "Höre zu…")
                vibrate(short = true)
                startListeningInternal()
            }
        }, (delaySec * 1000).toLong())
    }

    private fun launchChatGptApp() {
        val settings = repository.getSettings()
        val intent = context.packageManager.getLaunchIntentForPackage(settings.chatGptPackage)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Konnte ChatGPT nicht starten: ${e.message}")
            }
        }
    }

    private fun transitionTo(step: LoopStep, detail: String) {
        _loopState.update {
            it.copy(
                step = step,
                statusDetail = detail,
                errorMessage = if (step == LoopStep.ERROR) it.errorMessage else null
            )
        }
    }

    private fun vibrate(short: Boolean) {
        val settings = repository.getSettings()
        if (!settings.vibrateOnTransitions) return

        try {
            val duration = if (short) 60L else 180L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(duration)
            }
        } catch (_: Exception) {}
    }

    fun cleanup() {
        stopLoop()
        speechManager?.destroy()
        ttsManager?.shutdown()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioPlaybackCallback != null) {
            try {
                audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback)
            } catch (_: Exception) {}
        }
        VoiceLoopAccessibilityService.listener = null
    }

    companion object {
        const val TAG = "VoiceLoopController"
    }
}
