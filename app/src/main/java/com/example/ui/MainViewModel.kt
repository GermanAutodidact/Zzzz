package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VoiceLoopApp
import com.example.data.model.AppSettings
import com.example.data.model.ConversationTurn
import com.example.data.model.DiagnosticLog
import com.example.data.model.LoopState
import com.example.service.VoiceLoopAccessibilityService
import com.example.service.VoiceLoopForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReadinessStatus(
    val hasAudioPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isBatteryOptimizedIgnored: Boolean = false,
    val isChatGptInstalled: Boolean = false,
    val isDexModeActive: Boolean = false
) {
    val isFullyReady: Boolean
        get() = hasAudioPermission && hasOverlayPermission && isAccessibilityEnabled && isChatGptInstalled
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as VoiceLoopApp).repository

    val settings: StateFlow<AppSettings> = repository.settingsFlow
    val recentTurns: StateFlow<List<ConversationTurn>> = repository.recentTurns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentLogs: StateFlow<List<DiagnosticLog>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val turnCount: StateFlow<Int> = repository.turnCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isServiceActive: StateFlow<Boolean> = VoiceLoopForegroundService.isServiceActive
    val isAccessibilityRunning: StateFlow<Boolean> = VoiceLoopAccessibilityService.isServiceRunning

    private val _readiness = MutableStateFlow(ReadinessStatus())
    val readiness: StateFlow<ReadinessStatus> = _readiness.asStateFlow()

    private val _inspectorResult = MutableStateFlow<String?>(null)
    val inspectorResult: StateFlow<String?> = _inspectorResult.asStateFlow()

    init {
        refreshReadiness()
    }

    fun refreshReadiness() {
        val context = getApplication<Application>()
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasOverlay = Settings.canDrawOverlays(context)
        val isAccRunning = VoiceLoopAccessibilityService.isServiceRunning.value

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        val isInstalled = try {
            context.packageManager.getPackageInfo(repository.getSettings().chatGptPackage, 0)
            true
        } catch (_: Exception) {
            false
        }

        val isDex = try {
            val config = context.resources.configuration
            val configClass = config.javaClass
            val semDesktopModeEnabledField = configClass.getField("SEM_DESKTOP_MODE_ENABLED")
            val semDesktopModeEnabled = semDesktopModeEnabledField.getInt(configClass)
            val semDesktopModeStateField = configClass.getField("semDesktopModeState")
            val semDesktopModeState = semDesktopModeStateField.getInt(config)
            semDesktopModeState == semDesktopModeEnabled
        } catch (_: Exception) {
            val config = context.resources.configuration
            val uiMode = config.uiMode
            (uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_DESK ||
            config.toString().contains("desktopMode=1") ||
            config.toString().contains("semDesktopModeState=1")
        }

        _readiness.value = ReadinessStatus(
            hasAudioPermission = hasAudio,
            hasOverlayPermission = hasOverlay,
            isAccessibilityEnabled = isAccRunning,
            isBatteryOptimizedIgnored = isBatteryIgnored,
            isChatGptInstalled = isInstalled,
            isDexModeActive = isDex
        )
    }

    fun startLoop(context: Context) {
        VoiceLoopForegroundService.start(context)
    }

    fun stopLoop(context: Context) {
        VoiceLoopForegroundService.stop(context)
    }

    fun pauseLoop() {
        val controller = VoiceLoopForegroundService.activeController
        controller?.pauseLoop()
    }

    fun resumeLoop() {
        val controller = VoiceLoopForegroundService.activeController
        controller?.resumeLoop()
    }

    fun restartSpeech() {
        val controller = VoiceLoopForegroundService.activeController
        controller?.restartListening()
    }

    fun updateSettings(newSettings: AppSettings) {
        repository.updateSettings(newSettings)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun testSendPrompt(prompt: String) {
        val service = VoiceLoopAccessibilityService.instance
        if (service == null) {
            _inspectorResult.value = "Bedienungshilfe-Dienst ist nicht aktiv"
            return
        }
        _inspectorResult.value = "Sende Testprompt an ChatGPT…"
        service.sendPrompt(prompt) { success, msg ->
            _inspectorResult.value = if (success) "Erfolg: $msg" else "Fehlgeschlagen: $msg"
        }
    }

    fun testCoordinateSend() {
        val service = VoiceLoopAccessibilityService.instance
        if (service == null) {
            _inspectorResult.value = "Bedienungshilfe-Dienst ist nicht aktiv"
            return
        }
        _inspectorResult.value = "Führe globalen Koordinaten-Fallback-Klick (dispatchGesture) aus…"
        service.dispatchGlobalCoordinateSendFallback { success, msg ->
            _inspectorResult.value = if (success) "Erfolg: $msg" else "Fehlgeschlagen: $msg"
        }
    }

    fun testReadAloud() {
        val service = VoiceLoopAccessibilityService.instance
        if (service == null) {
            _inspectorResult.value = "Bedienungshilfe-Dienst ist nicht aktiv"
            return
        }
        _inspectorResult.value = "Trigger Vorlesen…"
        service.triggerReadAloud { success, msg ->
            _inspectorResult.value = if (success) "Vorlesen aktiv: $msg" else "Fehler: $msg"
        }
    }

    fun getActiveLoopState(): StateFlow<LoopState>? {
        return VoiceLoopForegroundService.activeController?.loopState
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openBatterySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun openChatGptStoreOrApp(context: Context) {
        val pkg = repository.getSettings().chatGptPackage
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent)
        } else {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }
}
