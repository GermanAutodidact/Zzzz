package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_turns")
data class ConversationTurn(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userInputText: String,
    val chatGptResponseText: String = "",
    val durationMs: Long = 0,
    val status: String = "SUCCESS",
    val mode: String = "AUTO_FALLBACK"
)
