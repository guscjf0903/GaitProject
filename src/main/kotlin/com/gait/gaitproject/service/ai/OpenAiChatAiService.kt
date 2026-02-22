package com.gait.gaitproject.service.ai

import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Service

/**
 * OpenAI 기반 스트리밍 채팅 서비스.
 *
 * - Spring AI의 StreamingChatModel을 사용합니다.
 * - `app.ai.use-stub=false` 이고, `spring.ai.openai.api-key`가 비어있지 않을 때만 활성화됩니다.
 */
@Service
@ConditionalOnExpression(
    "\${app.ai.use-stub:true} == false and '\${spring.ai.openai.api-key:}' != '' and '\${spring.ai.model.chat:none}' == 'openai'"
)
class OpenAiChatAiService(
    private val streamingChatModel: StreamingChatModel
) : AiService {
    override fun streamAnswer(prompt: String, onChunk: (String) -> Unit) {
        // StreamingChatModel#stream(String) 기본 구현은 Flux<String>을 반환합니다.
        streamingChatModel.stream(prompt)
            .doOnNext { chunk -> if (chunk.isNotBlank()) onChunk(chunk) }
            .blockLast()
    }
}

