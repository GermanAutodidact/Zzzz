package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.example.R
import com.example.data.model.LoopState
import com.example.data.model.LoopStep

class FloatingHudManager(
    private val context: Context,
    private val onPauseResumeClicked: () -> Unit,
    private val onRestartSpeechClicked: () -> Unit,
    private val onStopClicked: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var hudView: View? = null
    private var isExpanded = true

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 50
        y = 150
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (hudView != null) return
        if (!Settings.canDrawOverlays(context)) return

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_floating_hud, null)
        hudView = view

        val dragHandle = view.findViewById<View>(R.id.hud_drag_handle)
        val btnPauseResume = view.findViewById<ImageButton>(R.id.btn_hud_pause_resume)
        val btnRestart = view.findViewById<ImageButton>(R.id.btn_hud_restart)
        val btnStop = view.findViewById<ImageButton>(R.id.btn_hud_stop)
        val btnToggleExpand = view.findViewById<ImageButton>(R.id.btn_hud_toggle_expand)

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(hudView, layoutParams)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        btnPauseResume.setOnClickListener { onPauseResumeClicked() }
        btnRestart.setOnClickListener { onRestartSpeechClicked() }
        btnStop.setOnClickListener { onStopClicked() }
        btnToggleExpand.setOnClickListener {
            isExpanded = !isExpanded
            updateExpansionState()
        }

        try {
            windowManager.addView(view, layoutParams)
        } catch (e: Exception) {
            hudView = null
        }
    }

    private fun updateExpansionState() {
        val view = hudView ?: return
        val expandedContainer = view.findViewById<View>(R.id.hud_expanded_content)
        val toggleBtn = view.findViewById<ImageButton>(R.id.btn_hud_toggle_expand)
        if (isExpanded) {
            expandedContainer.visibility = View.VISIBLE
            toggleBtn.setImageResource(android.R.drawable.arrow_up_float)
        } else {
            expandedContainer.visibility = View.GONE
            toggleBtn.setImageResource(android.R.drawable.arrow_down_float)
        }
    }

    fun updateState(state: LoopState) {
        val view = hudView ?: return

        val statusText = view.findViewById<TextView>(R.id.tv_hud_status)
        val detailText = view.findViewById<TextView>(R.id.tv_hud_detail)
        val transcriptionText = view.findViewById<TextView>(R.id.tv_hud_transcription)
        val progressBar = view.findViewById<ProgressBar>(R.id.hud_volume_bar)
        val btnPauseResume = view.findViewById<ImageButton>(R.id.btn_hud_pause_resume)
        val statusIndicator = view.findViewById<View>(R.id.hud_status_dot)

        val (label, dotColorRes) = when (state.step) {
            LoopStep.IDLE -> "Bereit" to R.color.hud_idle
            LoopStep.LISTENING -> "🎙 Höre zu…" to R.color.hud_listening
            LoopStep.PROCESSING_SPEECH -> "⚙ Verarbeite…" to R.color.hud_sending
            LoopStep.SENDING_TO_CHATGPT -> "🚀 Sende an ChatGPT…" to R.color.hud_sending
            LoopStep.WAITING_CHATGPT_REPLY -> "⏳ ChatGPT antwortet…" to R.color.hud_waiting
            LoopStep.READING_ALOUD -> "🔊 Lese vor…" to R.color.hud_speaking
            LoopStep.COOLDOWN -> "🔁 Nächste Eingabe…" to R.color.hud_listening
            LoopStep.PAUSED -> "⏸ Pausiert" to R.color.hud_paused
            LoopStep.ERROR -> "⚠ Fehler" to R.color.hud_error
        }

        statusText.text = label
        detailText.text = state.statusDetail
        statusIndicator.setBackgroundResource(dotColorRes)

        if (state.currentTranscription.isNotBlank()) {
            transcriptionText.visibility = View.VISIBLE
            transcriptionText.text = "\"${state.currentTranscription}\""
        } else {
            transcriptionText.visibility = View.GONE
        }

        if (state.step == LoopStep.LISTENING) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = (state.audioVolumeLevel * 100).toInt()
        } else {
            progressBar.visibility = View.GONE
        }

        if (state.step == LoopStep.PAUSED) {
            btnPauseResume.setImageResource(android.R.drawable.ic_media_play)
        } else {
            btnPauseResume.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    fun hide() {
        if (hudView != null) {
            try {
                windowManager.removeView(hudView)
            } catch (_: Exception) {}
            hudView = null
        }
    }
}
