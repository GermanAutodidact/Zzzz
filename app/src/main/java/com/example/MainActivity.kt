package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InspectorScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupWizardScreen
import com.example.ui.theme.MyApplicationTheme

enum class ScreenDestination(val title: String) {
    DASHBOARD("VoiceLoop"),
    SETUP("Einrichten"),
    INSPECTOR("Inspektor"),
    LOGS("Protokoll"),
    SETTINGS("Einstellungen")
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshReadiness()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(ScreenDestination.DASHBOARD) }
    val readiness by viewModel.readiness.collectAsState()

    BackHandler(enabled = currentScreen != ScreenDestination.DASHBOARD) {
        currentScreen = ScreenDestination.DASHBOARD
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            // Samsung DeX / Desktop / Tablet layout with side NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight(),
                    header = {
                        Text(
                            text = if (readiness.isDexModeActive) "DeX" else "Voice",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                ) {
                    NavigationRailItem(
                        selected = currentScreen == ScreenDestination.DASHBOARD,
                        onClick = { currentScreen = ScreenDestination.DASHBOARD },
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Dashboard", modifier = Modifier.size(24.dp)) },
                        label = { Text("Dialog") },
                        modifier = Modifier.testTag("nav_dashboard")
                    )
                    NavigationRailItem(
                        selected = currentScreen == ScreenDestination.SETUP,
                        onClick = { currentScreen = ScreenDestination.SETUP },
                        icon = { Icon(Icons.Default.Checklist, contentDescription = "Einrichten", modifier = Modifier.size(24.dp)) },
                        label = { Text("Setup") },
                        modifier = Modifier.testTag("nav_setup")
                    )
                    NavigationRailItem(
                        selected = currentScreen == ScreenDestination.INSPECTOR,
                        onClick = { currentScreen = ScreenDestination.INSPECTOR },
                        icon = { Icon(Icons.Default.BugReport, contentDescription = "Inspektor", modifier = Modifier.size(24.dp)) },
                        label = { Text("Test") },
                        modifier = Modifier.testTag("nav_inspector")
                    )
                    NavigationRailItem(
                        selected = currentScreen == ScreenDestination.LOGS,
                        onClick = { currentScreen = ScreenDestination.LOGS },
                        icon = { Icon(Icons.Default.Article, contentDescription = "Protokoll", modifier = Modifier.size(24.dp)) },
                        label = { Text("Logs") },
                        modifier = Modifier.testTag("nav_logs")
                    )
                    NavigationRailItem(
                        selected = currentScreen == ScreenDestination.SETTINGS,
                        onClick = { currentScreen = ScreenDestination.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Optionen", modifier = Modifier.size(24.dp)) },
                        label = { Text("Optionen") },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentScreen.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    if (readiness.isDexModeActive) {
                                        Text(
                                            text = " • Samsung DeX Modus",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 900.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            ScreenRouter(currentScreen, viewModel) { currentScreen = it }
                        }
                    }
                }
            }
        } else {
            // Phone / Compact Portrait Layout
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = currentScreen.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == ScreenDestination.DASHBOARD,
                            onClick = { currentScreen = ScreenDestination.DASHBOARD },
                            icon = { Icon(Icons.Default.Mic, contentDescription = "Dashboard", modifier = Modifier.size(24.dp)) },
                            label = { Text("Dialog") },
                            modifier = Modifier.testTag("nav_dashboard")
                        )
                        NavigationBarItem(
                            selected = currentScreen == ScreenDestination.SETUP,
                            onClick = { currentScreen = ScreenDestination.SETUP },
                            icon = { Icon(Icons.Default.Checklist, contentDescription = "Einrichten", modifier = Modifier.size(24.dp)) },
                            label = { Text("Setup") },
                            modifier = Modifier.testTag("nav_setup")
                        )
                        NavigationBarItem(
                            selected = currentScreen == ScreenDestination.INSPECTOR,
                            onClick = { currentScreen = ScreenDestination.INSPECTOR },
                            icon = { Icon(Icons.Default.BugReport, contentDescription = "Inspektor", modifier = Modifier.size(24.dp)) },
                            label = { Text("Test") },
                            modifier = Modifier.testTag("nav_inspector")
                        )
                        NavigationBarItem(
                            selected = currentScreen == ScreenDestination.LOGS,
                            onClick = { currentScreen = ScreenDestination.LOGS },
                            icon = { Icon(Icons.Default.Article, contentDescription = "Protokoll", modifier = Modifier.size(24.dp)) },
                            label = { Text("Logs") },
                            modifier = Modifier.testTag("nav_logs")
                        )
                        NavigationBarItem(
                            selected = currentScreen == ScreenDestination.SETTINGS,
                            onClick = { currentScreen = ScreenDestination.SETTINGS },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Optionen", modifier = Modifier.size(24.dp)) },
                            label = { Text("Optionen") },
                            modifier = Modifier.testTag("nav_settings")
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    ScreenRouter(currentScreen, viewModel) { currentScreen = it }
                }
            }
        }
    }
}

@Composable
fun ScreenRouter(
    currentScreen: ScreenDestination,
    viewModel: MainViewModel,
    onNavigate: (ScreenDestination) -> Unit
) {
    when (currentScreen) {
        ScreenDestination.DASHBOARD -> DashboardScreen(
            viewModel = viewModel,
            onNavigateToSetup = { onNavigate(ScreenDestination.SETUP) },
            onNavigateToSettings = { onNavigate(ScreenDestination.SETTINGS) },
            onNavigateToInspector = { onNavigate(ScreenDestination.INSPECTOR) }
        )
        ScreenDestination.SETUP -> SetupWizardScreen(
            viewModel = viewModel,
            onFinish = { onNavigate(ScreenDestination.DASHBOARD) }
        )
        ScreenDestination.INSPECTOR -> InspectorScreen(
            viewModel = viewModel
        )
        ScreenDestination.LOGS -> LogsScreen(
            viewModel = viewModel
        )
        ScreenDestination.SETTINGS -> SettingsScreen(
            viewModel = viewModel
        )
    }
}
