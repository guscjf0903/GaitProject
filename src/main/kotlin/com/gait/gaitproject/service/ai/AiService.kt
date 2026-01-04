package com.gait.gaitproject.service.ai

interface AiService {
    fun streamAnswer(prompt: String, onChunk: (String) -> Unit)
}


