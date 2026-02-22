package com.gait.gaitproject.service.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.gait.gaitproject.domain.chat.entity.Message
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.ResponseFormat
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Service

data class AiCommitSummary(
    val keyPoint: String? = null,
    val shortSummary: String? = null,
    val longSummary: String? = null
)

/**
 * 커밋 요약 생성(AI)
 *
 * - 1차 목표: 커밋 생성 시 `shortSummary`, `longSummary`를 자동 생성(필요 시 keyPoint도 보강)
 * - OpenAI JSON mode(JSON_OBJECT)로 구조화된 결과만 받도록 강제합니다.
 */
@Service
@ConditionalOnExpression(
    "\${app.ai.use-stub:true} == false and '\${spring.ai.openai.api-key:}' != '' and '\${spring.ai.model.chat:none}' == 'openai'"
)
class CommitSummaryAiService(
    private val chatModel: ChatModel,
    private val objectMapper: ObjectMapper,
    @Value("\${app.ai.commit-summary.model:gpt-4o-mini}")
    private val summaryModel: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun summarize(pendingMessages: List<Message>, parentLongSummary: String?): AiCommitSummary {
        if (pendingMessages.isEmpty()) return AiCommitSummary()

        val transcript = pendingMessages
            .take(60)
            .joinToString(separator = "\n") { msg ->
                val content = msg.content.replace("\n", " ").take(800)
                "${msg.role}: $content"
            }

        val userText = buildString {
            appendLine("아래는 대화 로그입니다. 이 로그를 기반으로 Git 커밋 요약을 생성하세요.")
            appendLine("반드시 JSON만 출력하세요. (설명/마크다운 금지)")
            appendLine()
            appendLine("출력 JSON 스키마:")
            appendLine("""{ "keyPoint": string, "shortSummary": string, "longSummary": string }""")
            appendLine()
            appendLine("작성 규칙:")
            appendLine("- keyPoint: 10~60자 내의 한국어 제목(핵심만)")
            appendLine("- shortSummary: 2~4문장으로 변경/결정 요약")
            appendLine("- longSummary: 5~12줄로 자세히(배경/결정/근거/다음 할 일 포함 가능)")
            appendLine("- 사실만 기반으로 작성(없는 내용 만들지 않기)")
            appendLine()
            if (!parentLongSummary.isNullOrBlank()) {
                appendLine("[PARENT_LONG_SUMMARY]")
                appendLine(parentLongSummary.take(4000))
                appendLine()
            }
            appendLine("[TRANSCRIPT]")
            appendLine(transcript)
        }

        val jsonMode = ResponseFormat().apply { type = ResponseFormat.Type.JSON_OBJECT }
        val options = OpenAiChatOptions.builder()
            .model(summaryModel)
            .temperature(0.2)
            .responseFormat(jsonMode)
            .build()

        return try {
            val response = chatModel.call(Prompt(userText, options))
            val json = response.result.output.text
            objectMapper.readValue(json, AiCommitSummary::class.java)
        } catch (e: Exception) {
            logger.warn("Commit summary AI failed. Fallback to empty summary. reason={}", e.message)
            AiCommitSummary()
        }
    }
}

