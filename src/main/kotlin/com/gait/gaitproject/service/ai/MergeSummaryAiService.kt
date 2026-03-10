package com.gait.gaitproject.service.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.ResponseFormat
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Service

data class AiMergeSummary(
    val keyPoint: String? = null,
    val shortSummary: String? = null,
    val longSummary: String? = null
)

/**
 * 지능형 머지(Deep Merge)를 위한 AI 서비스
 * Master/Pro 플랜 사용자가 브랜치를 머지할 때, 두 브랜치의 요약을 지능적으로 통합합니다.
 */
@Service
@ConditionalOnExpression(
    "\${app.ai.use-stub:true} == false and '\${spring.ai.openai.api-key:}' != '' and '\${spring.ai.model.chat:none}' == 'openai'"
)
class MergeSummaryAiService(
    private val chatModel: ChatModel,
    private val objectMapper: ObjectMapper,
    @Value("\${app.ai.merge-summary.model:gpt-4o-mini}")
    private val mergeModel: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun summarizeMerge(baseSummaries: List<String>, targetSummaries: List<String>, userNote: String?): AiMergeSummary {
        val baseText = baseSummaries.joinToString("\n- ")
        val targetText = targetSummaries.joinToString("\n- ")

        val userText = buildString {
            appendLine("아래는 Git 기반 대화 시스템의 두 브랜치(Base와 Target)에서 발생한 대화 커밋 요약들입니다.")
            appendLine("이 두 브랜치를 하나로 병합(Merge)하기 위한 최종 커밋 요약을 생성하세요.")
            appendLine("반드시 JSON만 출력하세요. (설명/마크다운 금지)")
            appendLine()
            appendLine("출력 JSON 스키마:")
            appendLine("""{ "keyPoint": string, "shortSummary": string, "longSummary": string }""")
            appendLine()
            appendLine("작성 규칙:")
            appendLine("- keyPoint: 10~60자 내의 한국어 제목 (예: A 브랜치와 B 브랜치의 결론 통합)")
            appendLine("- shortSummary: 2~4문장으로 병합된 결과 요약")
            appendLine("- longSummary: 5~12줄로 자세히 기술 (중복 제거, 논리적 충돌 해소 결과, 시간 순서 명시)")
            appendLine("- 사실만 기반으로 작성할 것")
            if (!userNote.isNullOrBlank()) {
                appendLine()
                appendLine("[사용자 특별 지시사항 (User Note)]")
                appendLine(userNote)
            }
            appendLine()
            appendLine("[BASE BRANCH SUMMARIES (수용 대상)]")
            appendLine("- $baseText")
            appendLine()
            appendLine("[TARGET BRANCH SUMMARIES (병합 대상)]")
            appendLine("- $targetText")
        }

        val jsonMode = ResponseFormat().apply { type = ResponseFormat.Type.JSON_OBJECT }
        val options = OpenAiChatOptions.builder()
            .model(mergeModel)
            .temperature(0.3)
            .responseFormat(jsonMode)
            .build()

        return try {
            val response = chatModel.call(Prompt(userText, options))
            val json = response.result.output.text
            objectMapper.readValue(json, AiMergeSummary::class.java)
        } catch (e: Exception) {
            logger.warn("Merge summary AI failed. Fallback to empty summary. reason={}", e.message)
            AiMergeSummary(
                keyPoint = "Merged Branches",
                shortSummary = "AI 요약 생성에 실패하여 기본 메시지로 병합되었습니다.",
                longSummary = "AI 병합 실패. 수동 확인 필요."
            )
        }
    }
}
