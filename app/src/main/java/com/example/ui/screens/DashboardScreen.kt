package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConversationTurn
import com.example.data.model.LoopState
import com.example.data.model.LoopStep
import com.example.data.model.ReadAloudMode
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToSetup: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInspector: () -> Unit
) {
    val context = LocalContext.current
    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val readiness by viewModel.readiness.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val recentTurns by viewModel.recentTurns.collectAsState()
    val turnCount by viewModel.turnCount.collectAsState()

    val activeLoopStateFlow = viewModel.getActiveLoopState()
    val activeState by activeLoopStateFlow?.collectAsState() ?: androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(LoopState())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Samsung DeX Active Banner
        if (readiness.isDexModeActive) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🖥️",
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Samsung DeX Desktop-Modus aktiv",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Multi-Fenster-Unterstützung aktiviert: VoiceLoop und ChatGPT können parallel in getrennten Fenstern geöffnet sein.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // Readiness Status Warning Banner if not ready
        if (!readiness.isFullyReady) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warnung",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Einrichtung erforderlich",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Einige Berechtigungen oder Dienste fehlen für den automatischen Dialog.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = onNavigateToSetup,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("open_setup_button")
                        ) {
                            Text("Einrichten")
                        }
                    }
                }
            }
        }

        // Active State Hero Card
        item {
            ActiveStateHeroCard(
                isServiceActive = isServiceActive,
                state = activeState,
                onPauseResume = {
                    if (activeState.step == LoopStep.PAUSED) viewModel.resumeLoop()
                    else viewModel.pauseLoop()
                },
                onRestartSpeech = { viewModel.restartSpeech() }
            )
        }

        // Main Control Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isServiceActive) {
                    Button(
                        onClick = { viewModel.startLoop(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("toggle_loop_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sprach-Loop starten",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopLoop(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("toggle_loop_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loop beenden",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { viewModel.openChatGptStoreOrApp(context) },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("ChatGPT")
                }
            }
        }

        // Mode & Settings summary chips
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Modus: ${getModeLabel(settings.readAloudMode)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sprache: ${settings.speechLanguage} • Pause: ${settings.loopDelaySeconds}s • Durchläufe: $turnCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                }
            }
        }

        // Conversation History Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dialog-Verlauf (${recentTurns.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (recentTurns.isNotEmpty()) {
                    OutlinedButton(onClick = { viewModel.clearHistory() }) {
                        Text("Leeren", fontSize = 12.sp)
                    }
                }
            }
        }

        // Conversation Turn Cards
        if (recentTurns.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Noch keine Sprachdialoge aufgezeichnet.\nStarten Sie den Loop und sprechen Sie mit ChatGPT!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(recentTurns, key = { it.id }) { turn ->
                ConversationTurnItem(turn)
            }
        }
    }
}

@Composable
fun ActiveStateHeroCard(
    isServiceActive: Boolean,
    state: LoopState,
    onPauseResume: () -> Unit,
    onRestartSpeech: () -> Unit
) {
    val step = state.step
    val isListening = step == LoopStep.LISTENING
    val isPaused = step == LoopStep.PAUSED

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.15f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val stepColor by animateColorAsState(
        targetValue = when (step) {
            LoopStep.IDLE -> MaterialTheme.colorScheme.surfaceVariant
            LoopStep.LISTENING -> Color(0xFF10B981) // emerald
            LoopStep.PROCESSING_SPEECH, LoopStep.SENDING_TO_CHATGPT -> Color(0xFF38BDF8) // sky blue
            LoopStep.WAITING_CHATGPT_REPLY -> Color(0xFFF59E0B) // amber
            LoopStep.READING_ALOUD -> Color(0xFF8B5CF6) // violet
            LoopStep.COOLDOWN -> Color(0xFF10B981)
            LoopStep.PAUSED -> Color(0xFF64748B)
            LoopStep.ERROR -> Color(0xFFEF4444)
        },
        label = "stepColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated status icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(if (isServiceActive) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(stepColor.copy(alpha = 0.2f))
                    .border(2.dp, stepColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (step) {
                        LoopStep.IDLE -> Icons.Default.PlayArrow
                        LoopStep.LISTENING -> Icons.Default.Mic
                        LoopStep.PROCESSING_SPEECH, LoopStep.SENDING_TO_CHATGPT -> Icons.Default.PlayArrow
                        LoopStep.WAITING_CHATGPT_REPLY -> Icons.Default.Refresh
                        LoopStep.READING_ALOUD -> Icons.Default.VolumeUp
                        LoopStep.COOLDOWN -> Icons.Default.Mic
                        LoopStep.PAUSED -> Icons.Default.Pause
                        LoopStep.ERROR -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = stepColor,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = getStepTitle(step, isServiceActive),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isServiceActive) stepColor else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = state.statusDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Audio volume meter
            if (isListening && isServiceActive) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.audioVolumeLevel },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFF10B981).copy(alpha = 0.2f)
                )
            }

            // Current Transcription box
            if (state.currentTranscription.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${state.currentTranscription}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Quick controls when service is running
            if (isServiceActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onPauseResume) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPaused) "Fortsetzen" else "Pausieren")
                    }

                    OutlinedButton(onClick = onRestartSpeech) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Neu sprechen")
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationTurnItem(turn: ConversationTurn) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(turn.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sie",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = turn.userInputText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (turn.chatGptResponseText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ChatGPT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = turn.chatGptResponseText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

fun getStepTitle(step: LoopStep, isServiceActive: Boolean): String {
    if (!isServiceActive) return "Bereit zum Starten"
    return when (step) {
        LoopStep.IDLE -> "Bereit"
        LoopStep.LISTENING -> "Höre zu…"
        LoopStep.PROCESSING_SPEECH -> "Verarbeite Sprache…"
        LoopStep.SENDING_TO_CHATGPT -> "Sende an ChatGPT…"
        LoopStep.WAITING_CHATGPT_REPLY -> "ChatGPT generiert Antwort…"
        LoopStep.READING_ALOUD -> "Antwort wird vorgelesen…"
        LoopStep.COOLDOWN -> "Nächste Eingabe startet gleich…"
        LoopStep.PAUSED -> "Pausiert"
        LoopStep.ERROR -> "Fehler aufgetreten"
    }
}

fun getModeLabel(mode: ReadAloudMode): String {
    return when (mode) {
        ReadAloudMode.AUTO_FALLBACK -> "Auto (Native + Fallback TTS)"
        ReadAloudMode.NATIVE_ONLY -> "ChatGPT Native Voice"
        ReadAloudMode.TTS_ONLY -> "Direktes Android TTS"
    }
}
