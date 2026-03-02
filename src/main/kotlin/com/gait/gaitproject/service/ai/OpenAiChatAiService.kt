package com.gait.gaitproject.service.ai

import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Value
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
    private val streamingChatModel: StreamingChatModel,
    @Value("\${spring.ai.openai.chat.options.model:gpt-4o-mini}") private val defaultModel: String
) : AiService {
    override fun streamAnswer(prompt: String, onChunk: (String) -> Unit): AiStreamResult {
        val responseBuilder = StringBuilder()
        var promptTokens: Int? = null
        var completionTokens: Int? = null
        var totalTokens: Int? = null
        var modelName = defaultModel

        // OpenAI 스트리밍 시 토큰 사용량을 받기 위해 streamUsage 옵션을 명시적으로 활성화합니다.
        val options = OpenAiChatOptions.builder()
            .model(defaultModel)
            .streamUsage(true)
            .build()

        val chatResponseFlux = streamingChatModel.stream(Prompt(prompt, options))
        
        chatResponseFlux.doOnNext { response ->
            val content = response.result?.output?.text ?: ""
            if (content.isNotEmpty()) {
                responseBuilder.append(content)
                onChunk(content)
            }
            
            println("=== CHAT RESPONSE METADATA ===")
            println(response.metadata)
            
            // 토큰 사용량 정보는 보통 마지막 청크의 metadata에 포함되어 옵니다.
            val usage = response.metadata?.usage
            if (usage != null) {
                // 한 번이라도 값이 오면 기록 (null이 아닌 유효한 값일 때만 업데이트)
                usage.promptTokens?.toInt()?.let { if (it > 0) promptTokens = it }
                usage.completionTokens?.toInt()?.let { if (it > 0) completionTokens = it }
                usage.totalTokens?.toInt()?.let { if (it > 0) totalTokens = it }
            }
        }.blockLast()

        return AiStreamResult(
            fullResponse = responseBuilder.toString(),
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            modelName = modelName
        )
    }
}



