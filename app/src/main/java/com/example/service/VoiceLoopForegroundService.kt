package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.controller.VoiceLoopController
import com.example.data.local.SettingsPreferences
import com.example.data.local.VoiceLoopDatabase
import com.example.data.model.LoopState
import com.example.data.model.LoopStep
import com.example.data.repository.VoiceLoopRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceLoopForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: VoiceLoopController? = null
    private var hudManager: FloatingHudManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isServiceActive.value = true

        createNotificationChannel()

        val db = VoiceLoopDatabase.getInstance(this)
        val prefs = SettingsPreferences(this)
        val repository = VoiceLoopRepository(db.conversationDao(), db.diagnosticLogDao(), prefs, serviceScope)

        controller = VoiceLoopController(this, repository, serviceScope)
        activeController = controller

        hudManager = FloatingHudManager(
            context = this,
            onPauseResumeClicked = {
                val state = controller?.loopState?.value
                if (state?.step == LoopStep.PAUSED) {
                    controller?.resumeLoop()
                } else {
                    controller?.pauseLoop()
                }
            },
            onRestartSpeechClicked = {
                controller?.restartListening()
            },
            onStopClicked = {
                stopSelf()
            }
        )

        // Monitor loop state for notification and HUD updates
        serviceScope.launch {
            controller?.loopState?.collect { state ->
                updateNotification(state)
                hudManager?.updateState(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        startForegroundWithNotification()

        when (action) {
            ACTION_START -> {
                val settings = SettingsPreferences(this).getSettings()
                if (settings.showFloatingHud) {
                    hudManager?.show()
                }
                controller?.startLoop()
            }
            ACTION_PAUSE -> controller?.pauseLoop()
            ACTION_RESUME -> controller?.resumeLoop()
            ACTION_RESTART_SPEECH -> controller?.restartListening()
            ACTION_STOP -> {
                controller?.stopLoop()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification(controller?.loopState?.value ?: LoopState())
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: LoopState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceLoopForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPaused = state.step == LoopStep.PAUSED
        val toggleActionIntent = Intent(this, VoiceLoopForegroundService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 2, toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusTitle = when (state.step) {
            LoopStep.IDLE -> "VoiceLoop: Bereit"
            LoopStep.LISTENING -> "VoiceLoop: Höre zu…"
            LoopStep.PROCESSING_SPEECH -> "VoiceLoop: Verarbeite Sprache…"
            LoopStep.SENDING_TO_CHATGPT -> "VoiceLoop: Sende an ChatGPT…"
            LoopStep.WAITING_CHATGPT_REPLY -> "VoiceLoop: ChatGPT antwortet…"
            LoopStep.READING_ALOUD -> "VoiceLoop: Lese Antwort vor…"
            LoopStep.COOLDOWN -> "VoiceLoop: Pause vor nächster Eingabe…"
            LoopStep.PAUSED -> "VoiceLoop: Pausiert"
            LoopStep.ERROR -> "VoiceLoop: Fehler aufgetreten"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(statusTitle)
            .setContentText(state.statusDetail)
            .setSmallIcon(R.drawable.hud_dot_circle)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) getString(R.string.notification_action_resume) else getString(R.string.notification_action_pause),
                togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(state: LoopState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    override fun onDestroy() {
        super.onDestroy()
        hudManager?.hide()
        hudManager = null
        controller?.cleanup()
        controller = null
        activeController = null
        serviceScope.cancel()
        _isServiceActive.value = false
        instance = null
    }

    companion object {
        const val CHANNEL_ID = "voiceloop_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.voiceloop.ACTION_START"
        const val ACTION_STOP = "com.example.voiceloop.ACTION_STOP"
        const val ACTION_PAUSE = "com.example.voiceloop.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.voiceloop.ACTION_RESUME"
        const val ACTION_RESTART_SPEECH = "com.example.voiceloop.ACTION_RESTART_SPEECH"

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        var instance: VoiceLoopForegroundService? = null
            private set

        var activeController: VoiceLoopController? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, VoiceLoopForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceLoopForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
