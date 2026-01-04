package com.gait.gaitproject.service.ai

import org.springframework.stereotype.Service
import java.time.Duration

@Service
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


