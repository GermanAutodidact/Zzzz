package com.example.data.repository

import com.example.data.local.ConversationDao
import com.example.data.local.DiagnosticLogDao
import com.example.data.local.SettingsPreferences
import com.example.data.model.AppSettings
import com.example.data.model.ConversationTurn
import com.example.data.model.DiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VoiceLoopRepository(
    private val conversationDao: ConversationDao,
    private val diagnosticLogDao: DiagnosticLogDao,
    private val settingsPreferences: SettingsPreferences,
    private val scope: CoroutineScope
) {
    val settingsFlow: StateFlow<AppSettings> = settingsPreferences.settingsFlow
    val recentTurns: Flow<List<ConversationTurn>> = conversationDao.getRecentTurns(50)
    val recentLogs: Flow<List<DiagnosticLog>> = diagnosticLogDao.getRecentLogs(100)
    val turnCount: Flow<Int> = conversationDao.getTurnCount()

    fun getSettings(): AppSettings = settingsPreferences.getSettings()

    fun updateSettings(settings: AppSettings) {
        settingsPreferences.updateSettings(settings)
    }

    fun log(tag: String, message: String, level: String = "INFO") {
        scope.launch(Dispatchers.IO) {
            try {
                diagnosticLogDao.insertLog(DiagnosticLog(tag = tag, message = message, level = level))
            } catch (_: Exception) {
                // Ignore DB logging error
            }
        }
    }

    suspend fun saveTurn(turn: ConversationTurn): Long {
        return conversationDao.insertTurn(turn)
    }

    suspend fun clearHistory() {
        conversationDao.clearAllTurns()
    }

    suspend fun clearLogs() {
        diagnosticLogDao.clearLogs()
    }
}
