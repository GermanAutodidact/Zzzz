package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceLoopAccessibilityService : AccessibilityService() {

    interface ChatGPTEventListener {
        fun onGenerationStarted() {}
        fun onGenerationProgress(currentText: String) {}
        fun onGenerationCompleted(finalText: String) {}
        fun onReadAloudTriggered(success: Boolean) {}
        fun onChatGptForegroundChanged(inForeground: Boolean) {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastStopButtonSeenTime: Long = 0
    private var isGenerating = false
    private var latestCapturedText = ""
    private var stabilizationRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceRunning.value = true
        Log.i(TAG, "VoiceLoopAccessibilityService erfolgreich verbunden")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""
        val isChatGpt = pkgName.contains("com.openai.chatgpt")

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            listener?.onChatGptForegroundChanged(isChatGpt)
        }

        if (!isChatGpt) return

        // Check for generation state changes
        inspectGenerationState()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service unterbrochen")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceRunning.value = false
    }

    /**
     * Resolves ChatGPT's root node in standard single-window mode as well as
     * in Samsung DeX multi-window / desktop freeform mode.
     */
    fun getChatGptRootNode(): AccessibilityNodeInfo? {
        // 1. Try active window first
        val active = rootInActiveWindow
        if (active != null && active.packageName?.contains("com.openai.chatgpt") == true) {
            return active
        }

        // 2. In Samsung DeX Desktop or Multi-Window mode, check all interactive desktop windows
        try {
            val windowList = windows
            for (w in windowList) {
                val r = w.root ?: continue
                if (r.packageName?.contains("com.openai.chatgpt") == true) {
                    return r
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Durchsuchen der DeX Multi-Window-Fenster", e)
        }

        // 3. Fallback to active window
        return active
    }

    data class DeviceScreenMetrics(
        val screenWidth: Int,
        val screenHeight: Int,
        val density: Float,
        val screenBounds: Rect,
        val insetsTop: Int = 0,
        val insetsBottom: Int = 0
    )

    /**
     * Resolves the device's current display resolution and window metrics.
     * Uses WindowMetrics on Android 11+ (API 30+) or DisplayMetrics real metrics on Android 10 (Galaxy S10).
     */
    fun getCurrentWindowMetrics(): DeviceScreenMetrics {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val currentMetrics = wm.currentWindowMetrics
            val bounds = currentMetrics.bounds
            val insets = currentMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars()
            )
            val density = resources.displayMetrics.density
            DeviceScreenMetrics(
                screenWidth = bounds.width(),
                screenHeight = bounds.height(),
                density = density,
                screenBounds = bounds,
                insetsTop = insets.top,
                insetsBottom = insets.bottom
            )
        } else {
            val dm = resources.displayMetrics
            val defaultDisplay = @Suppress("DEPRECATION") wm?.defaultDisplay
            val realDm = DisplayMetrics()
            if (defaultDisplay != null) {
                @Suppress("DEPRECATION")
                defaultDisplay.getRealMetrics(realDm)
            } else {
                realDm.setTo(dm)
            }
            DeviceScreenMetrics(
                screenWidth = realDm.widthPixels,
                screenHeight = realDm.heightPixels,
                density = realDm.density,
                screenBounds = Rect(0, 0, realDm.widthPixels, realDm.heightPixels)
            )
        }
    }

    /**
     * Resolves the global screen bounding rectangle of the ChatGPT window,
     * including floating resizable windows in Samsung DeX desktop mode.
     */
    fun getChatGptWindowBounds(): Rect {
        val screenMetrics = getCurrentWindowMetrics()
        val defaultBounds = Rect(0, 0, screenMetrics.screenWidth, screenMetrics.screenHeight)
        try {
            val windowList = windows
            for (w in windowList) {
                val r = w.root ?: continue
                if (r.packageName?.contains("com.openai.chatgpt") == true) {
                    val out = Rect()
                    w.getBoundsInScreen(out)
                    if (out.width() > 100 && out.height() > 100) {
                        return out
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Ermitteln der Fenstergrenzen", e)
        }

        val active = rootInActiveWindow
        if (active != null && active.packageName?.contains("com.openai.chatgpt") == true) {
            val out = Rect()
            active.getBoundsInScreen(out)
            if (out.width() > 100 && out.height() > 100) return out
        }

        return defaultBounds
    }

    /**
     * Fallback mechanism that uses global coordinate-based clicking (dispatchGesture)
     * calculating the Send button's relative position based on the current screen resolution (WindowMetrics).
     * This guarantees that the click accurately lands on the Send button regardless of whether the app is running
     * on a phone screen, in a split-screen layout, or inside a floating/maximized Samsung DeX desktop window.
     */
    fun dispatchGlobalCoordinateSendFallback(
        knownInputBounds: Rect? = null,
        knownWindowBounds: Rect? = null,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val screenMetrics = getCurrentWindowMetrics()
        val windowBounds = knownWindowBounds ?: getChatGptWindowBounds()
        val density = screenMetrics.density

        // Determine input bounds if not provided
        val inputBounds = knownInputBounds ?: run {
            val root = getChatGptRootNode()
            if (root != null) {
                val node = findInputNode(root, "")
                if (node != null) {
                    val r = Rect()
                    node.getBoundsInScreen(r)
                    if (r.width() > 0) r else null
                } else null
            } else null
        }

        val winWidth = windowBounds.width().toFloat()
        val winHeight = windowBounds.height().toFloat()

        val primaryTarget: PointF
        val secondaryTarget: PointF

        if (inputBounds != null && inputBounds.width() > 0) {
            // Case 1: Input text box was detected in the hierarchy.
            // Vertical position: Centered on input field, or near bottom for multiline text
            val targetY = if (inputBounds.height() > 70 * density) {
                inputBounds.bottom.toFloat() - (22 * density)
            } else {
                inputBounds.centerY().toFloat()
            }

            // Horizontal position: Directly to the right of the input field, scaled by screen density
            val offsetFromInput = (24 * density).coerceAtLeast(30f)
            val targetX = (inputBounds.right.toFloat() + offsetFromInput)
                .coerceIn(inputBounds.right.toFloat() + 8f, windowBounds.right.toFloat() - (16 * density))

            primaryTarget = PointF(targetX, targetY)

            // Secondary target: The absolute bottom-right trailing slot of the window
            val secX = windowBounds.right.toFloat() - (34 * density)
            val secY = targetY
            secondaryTarget = PointF(secX, secY)
        } else {
            // Case 2: Input box was not resolved due to nesting/overlays.
            // Calculate relative position strictly using WindowMetrics & window geometry.
            
            // In wide layouts (tablets, maximized DeX >= 640dp wide), ChatGPT restricts the chat column
            // to a centered container with max width ~768dp.
            val maxContentWidth = minOf(winWidth, 768f * density)
            val contentRight = windowBounds.centerX() + (maxContentWidth / 2f)

            val relX = if (winWidth >= 640 * density) {
                contentRight - (26 * density)
            } else {
                // Responsive ratio for handheld / narrow DeX windows (~93.2% across window width)
                windowBounds.left + (winWidth * 0.932f).coerceIn(winWidth - (52 * density), winWidth - (24 * density))
            }

            // Relative Y: Account for system bars or DeX taskbar insets from WindowMetrics
            val bottomInset = if (windowBounds.bottom >= screenMetrics.screenHeight - 10) {
                screenMetrics.insetsBottom.coerceAtLeast((16 * density).toInt())
            } else 0

            val relY = (windowBounds.bottom - bottomInset).toFloat() - (32 * density)

            primaryTarget = PointF(relX, relY)

            // Secondary target: Standard bottom-right slot
            val secX = windowBounds.right.toFloat() - (34 * density)
            val secY = windowBounds.bottom.toFloat() - (34 * density)
            secondaryTarget = PointF(secX, secY)
        }

        Log.i(
            TAG,
            "WindowMetrics Fallback: Screen=[${screenMetrics.screenWidth}x${screenMetrics.screenHeight}, d=${screenMetrics.density}], " +
            "Window=[${windowBounds.left},${windowBounds.top} - ${windowBounds.right},${windowBounds.bottom} (${winWidth.toInt()}x${winHeight.toInt()})], " +
            "Primary=(${primaryTarget.x}, ${primaryTarget.y}), Secondary=(${secondaryTarget.x}, ${secondaryTarget.y})"
        )

        // 1. Dispatch primary tap gesture (hardware-level touch event)
        val primaryTapped = clickAt(primaryTarget.x, primaryTarget.y, 70)

        // 2. Dispatch secondary gesture after 120ms if secondary target is distinct
        mainHandler.postDelayed({
            if (Math.abs(primaryTarget.x - secondaryTarget.x) > 16 || Math.abs(primaryTarget.y - secondaryTarget.y) > 16) {
                clickAt(secondaryTarget.x, secondaryTarget.y, 70)
            } else {
                // Micro-offset tap to pierce borders or subtle scaling in DeX
                clickAt(primaryTarget.x - (8 * density), primaryTarget.y, 70)
            }
        }, 120)

        // 3. Third confirmation tap after 220ms
        mainHandler.postDelayed({
            clickAt(primaryTarget.x, primaryTarget.y, 70)
        }, 220)

        // 4. Verify result after delay
        mainHandler.postDelayed({
            isGenerating = true
            listener?.onGenerationStarted()
            onResult?.invoke(
                primaryTapped,
                "Relative Send-Geste basierend auf WindowMetrics an (${primaryTarget.x.toInt()}, ${primaryTarget.y.toInt()}) abgesetzt"
            )
        }, 400)
    }

    /**
     * Injects prompt text into ChatGPT's input field and clicks the Send button.
     * Incorporates AccessibilityNodeInfo actions with automatic fallback to global coordinate dispatchGesture.
     */
    fun sendPrompt(text: String, customInputId: String = "", customSendId: String = "", onResult: (Boolean, String) -> Unit) {
        val root = getChatGptRootNode()
        if (root == null) {
            onResult(false, "Kein aktives ChatGPT-Fenster gefunden (auch nicht in DeX)")
            return
        }

        val inputNode = findInputNode(root, customInputId)
        if (inputNode == null) {
            onResult(false, "Eingabefeld in ChatGPT nicht gefunden")
            return
        }

        // 1. Focus input node
        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // 2. Set text via ACTION_SET_TEXT
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val setTextSuccess = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!setTextSuccess) {
            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }

        // 3. Move cursor to end: Essential for Jetpack Compose to trigger onValueChange & morph mic to send icon!
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
        }
        inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)

        val inputBounds = Rect()
        inputNode.getBoundsInScreen(inputBounds)
        val windowBounds = getChatGptWindowBounds()

        // 4. Multi-attempt loop to detect and tap Send button
        fun attemptSend(attempt: Int) {
            val sendRoot = getChatGptRootNode() ?: rootInActiveWindow ?: root
            val sendNode = findSendButton(sendRoot, inputNode, customSendId)

            if (sendNode != null) {
                val nodeBounds = Rect()
                sendNode.getBoundsInScreen(nodeBounds)
                val actionClicked = sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                // Dispatch gesture tap directly on node bounds as well
                if (nodeBounds.width() > 0 && nodeBounds.height() > 0) {
                    clickAt(nodeBounds.centerX().toFloat(), nodeBounds.centerY().toFloat(), 70)
                }

                // Verification check 400ms later:
                // If overlay or nesting prevented the action from sending (text is still present and not generating),
                // automatically engage the global coordinate fallback!
                mainHandler.postDelayed({
                    val freshRoot = getChatGptRootNode()
                    val freshInput = if (freshRoot != null) findInputNode(freshRoot, customInputId) else null
                    val textStillPresent = freshInput?.text?.toString()?.contains(text.take(10)) == true
                    val stopBtn = if (freshRoot != null) findStopButton(freshRoot) else null

                    if (textStillPresent && stopBtn == null && !isGenerating) {
                        Log.w(TAG, "AccessibilityNodeInfo click did not trigger send (overlay/nesting detected). Triggering global coordinate fallback!")
                        dispatchGlobalCoordinateSendFallback(inputBounds, windowBounds) { success, msg ->
                            onResult(success, "Nachricht über globalen Koordinaten-Fallback gesendet")
                        }
                    } else {
                        isGenerating = true
                        listener?.onGenerationStarted()
                        onResult(true, "Nachricht erfolgreich gesendet")
                    }
                }, 400)
                return
            }

            if (attempt < 3) {
                // Compose may need 200ms to morph icon from voice to send
                mainHandler.postDelayed({ attemptSend(attempt + 1) }, 250)
            } else {
                // Node not found or nested: Execute global coordinate fallback immediately!
                Log.i(TAG, "Send button node not found in accessibility tree. Executing global coordinate fallback!")
                dispatchGlobalCoordinateSendFallback(inputBounds, windowBounds) { success, msg ->
                    if (success) {
                        onResult(true, "Nachricht über globalen Koordinaten-Fallback gesendet")
                    } else {
                        onResult(false, "Senden über globale Koordinaten fehlgeschlagen: $msg")
                    }
                }
            }
        }

        // Delay first check slightly to let Compose process the text update
        mainHandler.postDelayed({ attemptSend(1) }, 250)
    }

    /**
     * Finds the message input field.
     */
    private fun findInputNode(root: AccessibilityNodeInfo, customId: String): AccessibilityNodeInfo? {
        if (customId.isNotBlank()) {
            val list = root.findAccessibilityNodeInfosByViewId(customId)
            if (!list.isNullOrEmpty()) return list.first()
        }

        // Search recursively for EditText or editable node
        return searchNode(root) { node ->
            node.isEditable ||
            node.className?.toString()?.contains("EditText", ignoreCase = true) == true ||
            node.viewIdResourceName?.contains("input", ignoreCase = true) == true ||
            node.viewIdResourceName?.contains("prompt", ignoreCase = true) == true ||
            node.hintText?.toString()?.contains("message", ignoreCase = true) == true ||
            node.hintText?.toString()?.contains("nachricht", ignoreCase = true) == true
        }
    }

    /**
     * Finds the Send button, strictly distinguishing it from attachment buttons on the left.
     */
    private fun findSendButton(root: AccessibilityNodeInfo, inputNode: AccessibilityNodeInfo?, customId: String): AccessibilityNodeInfo? {
        if (customId.isNotBlank()) {
            val list = root.findAccessibilityNodeInfosByViewId(customId)
            if (!list.isNullOrEmpty()) return list.first()
        }

        val sendKeywords = listOf("send", "senden", "absenden", "submit", "send prompt", "send message", "nachricht senden", "abschicken")

        // 1. Look by content description or text for explicit send keywords
        val byDesc = searchNode(root) { node ->
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            node != inputNode &&
            sendKeywords.any { desc.contains(it) || text.contains(it) } &&
            (node.isClickable || node.isEnabled || node.parent?.isClickable == true)
        }
        if (byDesc != null) return byDesc

        // 2. Look by known resource IDs
        val knownIds = listOf(
            "com.openai.chatgpt:id/send_button",
            "com.openai.chatgpt:id/button_send",
            "com.openai.chatgpt:id/send"
        )
        for (id in knownIds) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (!list.isNullOrEmpty()) return list.first()
        }

        // 3. Strict spatial search around inputNode:
        // The Send button is ALWAYS located to the RIGHT of the input field.
        // The Attachment (+) button is ALWAYS located to the LEFT of the input field.
        inputNode?.let { input ->
            val inputBounds = Rect()
            input.getBoundsInScreen(inputBounds)

            val rightCandidates = mutableListOf<AccessibilityNodeInfo>()
            fun collectRightButtons(node: AccessibilityNodeInfo, depth: Int = 0) {
                if (depth > 4) return
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    if (child != input) {
                        val cb = Rect()
                        child.getBoundsInScreen(cb)
                        // Must be to the right of input's center and vertically in line
                        if (cb.centerX() > inputBounds.centerX() &&
                            cb.bottom >= inputBounds.top - 80 &&
                            cb.top <= inputBounds.bottom + 80 &&
                            cb.width() in 20..300 && cb.height() in 20..300
                        ) {
                            rightCandidates.add(child)
                        }
                    }
                    collectRightButtons(child, depth + 1)
                }
            }

            var container: AccessibilityNodeInfo? = input.parent
            var depth = 0
            while (container != null && depth < 3 && rightCandidates.isEmpty()) {
                collectRightButtons(container)
                container = container.parent
                depth++
            }

            if (rightCandidates.isNotEmpty()) {
                // If any has a send keyword, choose it
                val keywordMatch = rightCandidates.find { node ->
                    val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                    sendKeywords.any { desc.contains(it) }
                }
                if (keywordMatch != null) return keywordMatch

                // Otherwise, pick the rightmost clickable element in that row
                return rightCandidates.maxByOrNull {
                    val r = Rect()
                    it.getBoundsInScreen(r)
                    r.right
                }
            }
        }

        // 4. Fallback search across root for bottom-right clickable element
        val rootBounds = Rect()
        root.getBoundsInScreen(rootBounds)
        val bottomCandidates = mutableListOf<AccessibilityNodeInfo>()
        searchNode(root) { node ->
            if (node != inputNode && (node.isClickable || node.isEnabled)) {
                val b = Rect()
                node.getBoundsInScreen(b)
                if (b.top >= rootBounds.height() * 0.65f &&
                    b.centerX() >= rootBounds.width() * 0.55f &&
                    b.width() in 24..260 && b.height() in 24..260
                ) {
                    bottomCandidates.add(node)
                }
            }
            false
        }
        if (bottomCandidates.isNotEmpty()) {
            return bottomCandidates.maxByOrNull {
                val r = Rect()
                it.getBoundsInScreen(r)
                r.right
            }
        }

        return null
    }

    /**
     * Checks if ChatGPT is currently generating (Stop button present).
     */
    private fun inspectGenerationState() {
        val root = getChatGptRootNode() ?: return
        val stopButton = findStopButton(root)

        if (stopButton != null) {
            lastStopButtonSeenTime = System.currentTimeMillis()
            if (!isGenerating) {
                isGenerating = true
                listener?.onGenerationStarted()
            }
            // Capture interim text
            val text = extractLatestAssistantText(root)
            if (text.isNotBlank() && text != latestCapturedText) {
                latestCapturedText = text
                listener?.onGenerationProgress(text)
            }
        } else if (isGenerating) {
            // Stop button disappeared: schedule stabilization check
            stabilizationRunnable?.let { mainHandler.removeCallbacks(it) }
            stabilizationRunnable = Runnable {
                val currentRoot = getChatGptRootNode() ?: return@Runnable
                if (findStopButton(currentRoot) == null) {
                    isGenerating = false
                    val finalText = extractLatestAssistantText(currentRoot)
                    if (finalText.isNotBlank()) {
                        latestCapturedText = finalText
                    }
                    listener?.onGenerationCompleted(latestCapturedText)
                }
            }
            mainHandler.postDelayed(stabilizationRunnable!!, 1200)
        }
    }

    /**
     * Looks for the "Stop generating" button.
     */
    private fun findStopButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stopKeywords = listOf("stop", "anhalten", "beenden", "stop generating", "generierung beenden")
        return searchNode(root) { node ->
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName?.lowercase() ?: ""
            stopKeywords.any { desc.contains(it) } || id.contains("stop")
        }
    }

    /**
     * Triggers the "Read Aloud" / "Laut vorlesen" feature in ChatGPT.
     */
    fun triggerReadAloud(onComplete: (Boolean, String) -> Unit) {
        val root = getChatGptRootNode()
        if (root == null) {
            onComplete(false, "Kein aktives ChatGPT-Fenster gefunden")
            return
        }

        // Method 1: Look for direct "Read Aloud" or speaker button
        val keywords = listOf("read aloud", "laut vorlesen", "vorlesen", "play", "speak", "audio abspielen")
        val directButton = searchNode(root) { node ->
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            keywords.any { desc.contains(it) || text.contains(it) } && node.isClickable
        }

        if (directButton != null) {
            val bounds = Rect()
            directButton.getBoundsInScreen(bounds)
            val actionClicked = directButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            var gestureClicked = false
            if (bounds.width() > 0 && bounds.height() > 0) {
                gestureClicked = clickAt(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
            val clicked = actionClicked || gestureClicked
            listener?.onReadAloudTriggered(clicked)
            onComplete(clicked, if (clicked) "Vorlesen über Button gestartet" else "Klick auf Vorlesen-Button fehlgeschlagen")
            return
        }

        // Method 2: Long press on latest assistant message to open context menu
        val assistantNode = findLatestAssistantMessageNode(root)
        if (assistantNode != null) {
            val rect = Rect()
            assistantNode.getBoundsInScreen(rect)
            val longClicked = assistantNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            if (!longClicked && rect.width() > 0 && rect.height() > 0) {
                longClickAt(rect.centerX().toFloat(), rect.centerY().toFloat())
            }

            // Wait for context menu to open
            mainHandler.postDelayed({
                val menuRoot = getChatGptRootNode() ?: root
                val menuItem = searchNode(menuRoot) { node ->
                    val text = node.text?.toString()?.lowercase() ?: ""
                    val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                    keywords.any { text.contains(it) || desc.contains(it) }
                }
                if (menuItem != null) {
                    val target = if (menuItem.isClickable) menuItem else (menuItem.parent?.takeIf { it.isClickable } ?: menuItem)
                    val menuBounds = Rect()
                    target.getBoundsInScreen(menuBounds)
                    val actionClicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    var gestureClicked = false
                    if (menuBounds.width() > 0 && menuBounds.height() > 0) {
                        gestureClicked = clickAt(menuBounds.centerX().toFloat(), menuBounds.centerY().toFloat())
                    }
                    val clicked = actionClicked || gestureClicked
                    listener?.onReadAloudTriggered(clicked)
                    onComplete(clicked, if (clicked) "Vorlesen über Menü gestartet" else "Klick auf Menüpunkt fehlgeschlagen")
                } else {
                    onComplete(false, "Menüpunkt 'Laut vorlesen' nicht gefunden")
                }
            }, 600)
            return
        }

        onComplete(false, "Kein Vorlese-Button und keine Antwort zum Vorlesen gefunden")
    }

    /**
     * Extracts the text of the latest assistant message from the node hierarchy.
     */
    fun extractLatestAssistantText(rootNode: AccessibilityNodeInfo? = null): String {
        val root = rootNode ?: getChatGptRootNode() ?: return ""
        val texts = mutableListOf<String>()
        collectCandidateTexts(root, texts)
        return texts.lastOrNull()?.trim() ?: ""
    }

    private fun collectCandidateTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        val text = node.text?.toString()?.trim()
        val className = node.className?.toString() ?: ""

        if (!text.isNullOrBlank() &&
            (className.contains("TextView") || className.contains("Text")) &&
            !node.isEditable &&
            text.length > 5 &&
            !isControlLabel(text)
        ) {
            list.add(text)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectCandidateTexts(child, list)
        }
    }

    private fun isControlLabel(text: String): Boolean {
        val lower = text.lowercase()
        return lower in listOf("message", "nachricht", "send", "senden", "stop", "chatgpt", "copy", "kopieren")
    }

    private fun findLatestAssistantMessageNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var lastCandidate: AccessibilityNodeInfo? = null
        searchNode(root) { node ->
            val text = node.text?.toString()?.trim()
            val className = node.className?.toString() ?: ""
            if (!text.isNullOrBlank() &&
                (className.contains("TextView") || className.contains("Text")) &&
                !node.isEditable &&
                text.length > 8 &&
                !isControlLabel(text)
            ) {
                lastCandidate = node
            }
            false
        }
        return lastCandidate
    }

    private fun searchNode(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = searchNode(child, predicate)
            if (result != null) return result
        }
        return null
    }

    fun clickAt(x: Float, y: Float, durationMs: Long = 70): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun longClickAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 700))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        const val TAG = "VoiceLoopAccService"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        var instance: VoiceLoopAccessibilityService? = null
            private set

        var listener: ChatGPTEventListener? = null
    }
}
