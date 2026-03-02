package com.gait.gaitproject.service.ai

data class AiStreamResult(
    val fullResponse: String,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val modelName: String? = null
)

interface AiService {
    fun streamAnswer(prompt: String, onChunk: (String) -> Unit): AiStreamResult
}


