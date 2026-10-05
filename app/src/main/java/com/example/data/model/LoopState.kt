package com.example.data.model

enum class LoopStep {
    IDLE,
    LISTENING,
    PROCESSING_SPEECH,
    SENDING_TO_CHATGPT,
    WAITING_CHATGPT_REPLY,
    READING_ALOUD,
    COOLDOWN,
    PAUSED,
    ERROR
}

data class LoopState(
    val step: LoopStep = LoopStep.IDLE,
    val currentTranscription: String = "",
    val isPartialTranscription: Boolean = false,
    val lastAssistantReply: String = "",
    val audioVolumeLevel: Float = 0f,
    val errorMessage: String? = null,
    val statusDetail: String = "Bereit",
    val turnCount: Int = 0
)
