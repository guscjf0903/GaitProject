package com.gait.gaitproject.service.ai

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Stub AI Service (개발/테스트용)
 * 실제 AI 연동 전까지 사용하거나, spring.ai.enabled=false일 때 사용
 */
@Service
@ConditionalOnProperty(name = ["app.ai.use-stub"], havingValue = "true", matchIfMissing = false)
class StubAiService : AiService {
    override fun streamAnswer(prompt: String, onChunk: (String) -> Unit) {
        val answer = "STUB_ANSWER: ${prompt.take(200)}"
        // 간단히 글자 단위로 스트리밍 흉내
        for (chunk in answer.chunked(20)) {
            onChunk(chunk)
            Thread.sleep(Duration.ofMillis(60).toMillis())
        }
    }
}


