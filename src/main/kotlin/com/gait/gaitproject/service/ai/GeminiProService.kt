package com.gait.gaitproject.service.ai

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Gemini Pro 서비스 (MASTER 플랜 일반 채팅, 머지)
 * 설계서 5.1: Master 플랜 일반 채팅, 머지(Deep Merge)
 * 
 * TODO: Spring AI 의존성 추가 후 실제 구현
 */
@Service
@ConditionalOnProperty(name = ["spring.ai.vertex.ai.gemini.pro.enabled"], havingValue = "true", matchIfMissing = false)
class GeminiProService : AiService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun streamAnswer(prompt: String, onChunk: (String) -> Unit): AiStreamResult {
        logger.warn("GeminiProService는 아직 구현되지 않았습니다. Spring AI 의존성을 추가하거나 직접 API 호출을 구현하세요.")
        // TODO: Spring AI ChatClient 또는 직접 HTTP 클라이언트로 Gemini Pro API 호출
        val answer = "Gemini Pro 응답 (구현 예정): ${prompt.take(200)}"
        answer.chunked(20).forEach { chunk ->
            onChunk(chunk)
            Thread.sleep(30)
        }
        
        return AiStreamResult(
            fullResponse = answer,
            promptTokens = 0,
            completionTokens = 0,
            totalTokens = 0,
            modelName = "gemini-1.5-pro"
        )
    }
}

