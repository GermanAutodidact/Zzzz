package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ReadAloudMode
import com.example.ui.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val settings by viewModel.settings.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Einstellungen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Section: Language
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sprache für Spracherkennung",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wählen Sie die Sprache für die präzise Spracherkennung:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("de-DE" to "Deutsch", "en-US" to "English", "fr-FR" to "Français").forEach { (code, label) ->
                            FilterChip(
                                selected = settings.speechLanguage == code,
                                onClick = { viewModel.updateSettings(settings.copy(speechLanguage = code)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // Section: Read Aloud Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vorlese-Modus",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wie soll die Antwort von ChatGPT vorgelesen werden?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeSelectionOption(
                            title = "Auto-Fallback (Empfohlen)",
                            description = "Versucht die originale ChatGPT-Stimme zu starten. Falls die UI oder das Netzwerk verzögert, liest das System-TTS sofort vor.",
                            isSelected = settings.readAloudMode == ReadAloudMode.AUTO_FALLBACK,
                            onSelect = { viewModel.updateSettings(settings.copy(readAloudMode = ReadAloudMode.AUTO_FALLBACK)) }
                        )

                        ModeSelectionOption(
                            title = "Nur native ChatGPT-Sprachausgabe",
                            description = "Verwendet ausschließlich den offiziellen 'Laut vorlesen'-Button in ChatGPT und wartet auf das Audio-Ende.",
                            isSelected = settings.readAloudMode == ReadAloudMode.NATIVE_ONLY,
                            onSelect = { viewModel.updateSettings(settings.copy(readAloudMode = ReadAloudMode.NATIVE_ONLY)) }
                        )

                        ModeSelectionOption(
                            title = "Direktes Android TTS (Extrem schnell)",
                            description = "Extrahiert den Antworttext sofort per Bedienungshilfe und liest ihn verzögerungsfrei über die Android-Stimme (z.B. Google/Samsung HD) vor.",
                            isSelected = settings.readAloudMode == ReadAloudMode.TTS_ONLY,
                            onSelect = { viewModel.updateSettings(settings.copy(readAloudMode = ReadAloudMode.TTS_ONLY)) }
                        )
                    }
                }
            }
        }

        // Section: Loop Timing
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pause vor nächster Spracheingabe",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${settings.loopDelaySeconds} Sekunde(n) Pause nach dem Vorlesen, bevor das Mikrofon wieder lauscht.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Slider(
                        value = settings.loopDelaySeconds.toFloat(),
                        onValueChange = { viewModel.updateSettings(settings.copy(loopDelaySeconds = it.toInt())) },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1s (Schnell)", style = MaterialTheme.typography.labelSmall)
                        Text("3s", style = MaterialTheme.typography.labelSmall)
                        Text("5s (Ruhig)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Section: TTS Speed
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vorlese-Geschwindigkeit (TTS)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Geschwindigkeit: ${"%.1f".format(settings.ttsSpeed)}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Slider(
                        value = settings.ttsSpeed,
                        onValueChange = { viewModel.updateSettings(settings.copy(ttsSpeed = it)) },
                        valueRange = 0.7f..1.6f,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Section: Automation Toggles
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingToggleRow(
                        title = "ChatGPT automatisch in den Vordergrund",
                        description = "Startet oder fokussiert die ChatGPT-App automatisch bei Loop-Start.",
                        checked = settings.autoLaunchChatGpt,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(autoLaunchChatGpt = it)) }
                    )

                    SettingToggleRow(
                        title = "Schwebendes Steuerungs-HUD anzeigen",
                        description = "Zeigt den schwebenden Status-Pill über ChatGPT an.",
                        checked = settings.showFloatingHud,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(showFloatingHud = it)) }
                    )

                    SettingToggleRow(
                        title = "Haptisches Feedback (Vibration)",
                        description = "Vibriert kurz bei Bereitschaft zum Sprechen und bei fertigen Antworten.",
                        checked = settings.vibrateOnTransitions,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(vibrateOnTransitions = it)) }
                    )
                }
            }
        }

        // Samsung Galaxy S10 Information Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Samsung Galaxy S10 / One UI 2.5 Hinweise",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Samsung One UI 2.5 schließt Hintergrund-Apps nach einigen Minuten aggressivem Energiesparen.\n• Öffnen Sie 'Einstellungen > Gerätewartung > Akku > App-Energieverwaltung'.\n• Fügen Sie VoiceLoop zu den 'Apps, die nie in Standby versetzt werden' hinzu.\n• Der Schwebende Dialog (HUD) kann per Finger an jede beliebige Stelle verschoben werden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ModeSelectionOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
