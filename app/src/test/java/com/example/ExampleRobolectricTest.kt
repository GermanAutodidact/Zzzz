package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VoiceLoopDatabase
import com.example.data.model.ConversationTurn
import com.example.data.model.DiagnosticLog
import com.example.data.model.LoopStep
import com.example.speech.TtsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29]) // Samsung Galaxy S10 Android 10 is API 29
class ExampleRobolectricTest {

    private lateinit var database: VoiceLoopDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VoiceLoopDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context matches VoiceLoop`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VoiceLoop", appName)
    }

    @Test
    fun `cleanTextForSpeech removes markdown and citations`() {
        val markdownText = "Hier ist ein Test mit **fettem** Text, *kursiv*, `inline code` und [Link](https://openai.com) sowie 【4:0†source】."
        val cleaned = TtsManager.cleanTextForSpeech(markdownText)
        assertTrue(!cleaned.contains("**"))
        assertTrue(!cleaned.contains("`"))
        assertTrue(!cleaned.contains("【"))
        assertTrue(!cleaned.contains("https://openai.com"))
        assertTrue(cleaned.contains("fettem"))
        assertTrue(cleaned.contains("Link"))
    }

    @Test
    fun `cleanTextForSpeech replaces code blocks`() {
        val textWithCode = "Hier ist die Lösung:\n```kotlin\nfun test() = true\n```\nFunktioniert super!"
        val cleaned = TtsManager.cleanTextForSpeech(textWithCode)
        assertTrue(cleaned.contains("Codeblock übersprungen"))
        assertTrue(cleaned.contains("Funktioniert super!"))
    }

    @Test
    fun `room conversation dao saves and retrieves turns`() = runBlocking {
        val dao = database.conversationDao()
        val turn = ConversationTurn(
            userInputText = "Wie ist das Wetter in Berlin?",
            chatGptResponseText = "Es ist sonnig bei 21 Grad.",
            durationMs = 2400,
            status = "SUCCESS",
            mode = "AUTO_FALLBACK"
        )
        val id = dao.insertTurn(turn)
        assertTrue(id > 0)

        val turns = dao.getAllTurns().first()
        assertEquals(1, turns.size)
        assertEquals("Wie ist das Wetter in Berlin?", turns[0].userInputText)
        assertEquals("Es ist sonnig bei 21 Grad.", turns[0].chatGptResponseText)

        dao.clearAllTurns()
        val emptyTurns = dao.getAllTurns().first()
        assertEquals(0, emptyTurns.size)
    }

    @Test
    fun `room diagnostic dao saves and retrieves logs`() = runBlocking {
        val dao = database.diagnosticLogDao()
        val log = DiagnosticLog(
            tag = "TestTag",
            message = "VoiceLoop Testnachricht",
            level = "INFO"
        )
        val id = dao.insertLog(log)
        assertTrue(id > 0)

        val logs = dao.getRecentLogs(10).first()
        assertEquals(1, logs.size)
        assertEquals("TestTag", logs[0].tag)
        assertEquals("VoiceLoop Testnachricht", logs[0].message)
    }

    @Test
    fun `verify all loop steps are properly defined`() {
        val steps = LoopStep.values()
        assertTrue(steps.contains(LoopStep.IDLE))
        assertTrue(steps.contains(LoopStep.LISTENING))
        assertTrue(steps.contains(LoopStep.SENDING_TO_CHATGPT))
        assertTrue(steps.contains(LoopStep.WAITING_CHATGPT_REPLY))
        assertTrue(steps.contains(LoopStep.READING_ALOUD))
        assertTrue(steps.contains(LoopStep.COOLDOWN))
        assertTrue(steps.contains(LoopStep.PAUSED))
    }
}
