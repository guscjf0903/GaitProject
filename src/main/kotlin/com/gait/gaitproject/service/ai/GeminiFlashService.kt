package com.gait.gaitproject.service.ai

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Gemini Flash 서비스 (FREE 플랜 기본, STANDARD 플랜 cheap pool)
 * 설계서 5.1: 무료플랜 일반 채팅, 커밋 요약, RAG 검색어 생성
 * 
 * TODO: Spring AI 의존성 추가 후 실제 구현
 * 현재는 StubAiService를 사용하거나, 직접 HTTP 클라이언트로 Gemini API 호출
 */
@Service
@ConditionalOnProperty(name = ["spring.ai.vertex.ai.gemini.enabled"], havingValue = "true", matchIfMissing = false)
class GeminiFlashService : AiService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun streamAnswer(prompt: String, onChunk: (String) -> Unit) {
        logger.warn("GeminiFlashService는 아직 구현되지 않았습니다. Spring AI 의존성을 추가하거나 직접 API 호출을 구현하세요.")
        // TODO: Spring AI ChatClient 또는 직접 HTTP 클라이언트로 Gemini API 호출
        // 임시로 Stub처럼 동작
        val answer = "Gemini Flash 응답 (구현 예정): ${prompt.take(200)}"
        answer.chunked(20).forEach { chunk ->
            onChunk(chunk)
            Thread.sleep(30)
        }
    }
}

