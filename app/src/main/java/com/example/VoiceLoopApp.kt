package com.example

import android.app.Application
import com.example.data.local.SettingsPreferences
import com.example.data.local.VoiceLoopDatabase
import com.example.data.repository.VoiceLoopRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VoiceLoopApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: VoiceLoopDatabase
        private set
    lateinit var settingsPreferences: SettingsPreferences
        private set
    lateinit var repository: VoiceLoopRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = VoiceLoopDatabase.getInstance(this)
        settingsPreferences = SettingsPreferences(this)
        repository = VoiceLoopRepository(
            database.conversationDao(),
            database.diagnosticLogDao(),
            settingsPreferences,
            appScope
        )
    }

    companion object {
        lateinit var instance: VoiceLoopApp
            private set
    }
}
