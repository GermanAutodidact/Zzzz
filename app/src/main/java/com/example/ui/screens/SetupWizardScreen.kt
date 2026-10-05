package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@Composable
fun SetupWizardScreen(
    viewModel: MainViewModel,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val readiness by viewModel.readiness.collectAsState()

    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshReadiness()
        if (granted) {
            Toast.makeText(context, "Mikrofon-Berechtigung erteilt", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshReadiness()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Einrichtungs-Assistent",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Für den vollautomatischen Sprachdialog mit der normalen ChatGPT-App auf Ihrem Samsung Galaxy S10 sind folgende Systemfunktionen erforderlich:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 1. Microphone
        item {
            PermissionCard(
                icon = Icons.Default.Mic,
                title = "1. Mikrofon-Berechtigung",
                description = "Erforderlich für präzise Spracherkennung in deutscher Sprache.",
                isGranted = readiness.hasAudioPermission,
                buttonText = if (readiness.hasAudioPermission) "Aktiviert" else "Mikrofon freigeben",
                testTag = "grant_mic_button",
                onClick = {
                    if (!readiness.hasAudioPermission) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }

        // 2. Accessibility Service
        item {
            PermissionCard(
                icon = Icons.Default.Accessibility,
                title = "2. Bedienungshilfe-Dienst",
                description = "Erlaubt das automatische Übertragen Ihres Texts in ChatGPT, Erkennen von Antworten und Starten des Vorlesens.",
                subNote = "Tippen Sie auf 'Aktivieren', wählen Sie 'Installierte Apps' > 'VoiceLoop' und schalten Sie den Schalter ein.",
                isGranted = readiness.isAccessibilityEnabled,
                buttonText = if (readiness.isAccessibilityEnabled) "Aktiviert" else "Bedienungshilfe öffnen",
                testTag = "grant_accessibility_button",
                onClick = {
                    viewModel.openAccessibilitySettings(context)
                }
            )
        }

        // 3. Floating HUD / Overlay
        item {
            PermissionCard(
                icon = Icons.Default.Layers,
                title = "3. Über anderen Apps anzeigen",
                description = "Zeigt das kompakte Floating-HUD direkt über der ChatGPT-App an, damit Sie den Dialog immer im Blick haben.",
                isGranted = readiness.hasOverlayPermission,
                buttonText = if (readiness.hasOverlayPermission) "Aktiviert" else "Überlagerung erlauben",
                testTag = "grant_overlay_button",
                onClick = {
                    viewModel.openOverlaySettings(context)
                }
            )
        }

        // 4. Samsung One UI 2.5 Battery Optimization
        item {
            PermissionCard(
                icon = Icons.Default.BatteryChargingFull,
                title = "4. Samsung Akku-Optimierung",
                description = "Auf Samsung One UI 2.5 beendet das System Hintergrunddienste schnell. Bitte VoiceLoop von der Akku-Optimierung ausnehmen.",
                subNote = "Samsung S10: 'Nicht optimiert' auswählen. Gerätewartung > Akku > App nicht in Standby versetzen.",
                isGranted = readiness.isBatteryOptimizedIgnored,
                buttonText = if (readiness.isBatteryOptimizedIgnored) "Ausgenommen" else "Akku-Ausnahme anfordern",
                testTag = "grant_battery_button",
                onClick = {
                    viewModel.openBatterySettings(context)
                }
            )
        }

        // 5. ChatGPT App
        item {
            PermissionCard(
                icon = Icons.Default.QuestionAnswer,
                title = "5. Offizielle ChatGPT-App",
                description = "Die normale Android-App von OpenAI (com.openai.chatgpt) muss installiert sein.",
                isGranted = readiness.isChatGptInstalled,
                buttonText = if (readiness.isChatGptInstalled) "Installiert" else "ChatGPT öffnen / laden",
                testTag = "open_chatgpt_button",
                onClick = {
                    viewModel.openChatGptStoreOrApp(context)
                }
            )
        }

        // Done button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.refreshReadiness()
                    onFinish()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (readiness.isFullyReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(
                    text = if (readiness.isFullyReady) "Fertigstellen & zum Dashboard" else "Zurück zur Übersicht",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    subNote: String? = null,
    isGranted: Boolean,
    buttonText: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Erteilt",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (subNote != null) {
                Text(
                    text = subNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isGranted) {
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(buttonText)
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Erneut prüfen / Ändern", fontSize = 12.sp)
                }
            }
        }
    }
}
