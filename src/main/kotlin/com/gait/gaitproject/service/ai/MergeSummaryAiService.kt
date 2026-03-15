package com.gait.gaitproject.service.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.gait.gaitproject.service.workspace.MergeContext
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

@Service
@ConditionalOnExpression(
    "\${app.ai.use-stub:true} == false and '\${spring.ai.openai.api-key:}' != '' and '\${spring.ai.model.chat:none}' == 'openai'"
)
class MergeSummaryAiService(
    private val chatModel: ChatModel,
    private val objectMapper: ObjectMapper,
    @Value("\${app.ai.merge-summary.squash-model:gpt-4o-mini}")
    private val squashModel: String,
    @Value("\${app.ai.merge-summary.deep-model:gpt-4o}")
    private val deepModel: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun summarizeSquash(context: MergeContext, userNote: String?): AiMergeSummary {
        val prompt = buildSquashPrompt(context, userNote)
        val options = OpenAiChatOptions.builder()
            .model(squashModel)
            .temperature(0.3)
            .maxTokens(800)
            .responseFormat(jsonResponseFormat())
            .build()
        return callAi(prompt, options, "Squash")
    }

    fun summarizeDeep(context: MergeContext, userNote: String?): AiMergeSummary {
        val prompt = buildDeepPrompt(context, userNote)
        val options = OpenAiChatOptions.builder()
            .model(deepModel)
            .temperature(0.4)
            .maxTokens(2000)
            .responseFormat(jsonResponseFormat())
            .build()
        return callAi(prompt, options, "Deep")
    }

    private fun buildSquashPrompt(context: MergeContext, userNote: String?): String = buildString {
        appendLine("Git 기반 AI 대화 시스템에서 두 브랜치를 Squash Merge 합니다.")
        appendLine("아래 재료를 바탕으로 병합 커밋 요약을 생성하세요.")
        appendLine("반드시 JSON만 출력하세요. (설명/마크다운 금지)")
        appendLine()
        appendLine("출력 JSON 스키마:")
        appendLine("""{ "keyPoint": string, "shortSummary": string, "longSummary": string }""")
        appendLine()
        appendLine("작성 규칙:")
        appendLine("- keyPoint: 10~60자 한국어 제목 (병합 요지)")
        appendLine("- shortSummary: 2~4문장으로 병합된 핵심 내용 요약")
        appendLine("- longSummary: 5~10줄로 시간순 정리. 어떤 주제가 논의되었는지 나열.")
        appendLine("- 사실만 기반으로 작성할 것")

        if (!userNote.isNullOrBlank()) {
            appendLine()
            appendLine("[사용자 지시사항]")
            appendLine(userNote)
        }

        appendLine()
        appendLine("=== FROM 브랜치: ${context.fromMaterial.branchName} ===")
        appendBranchMaterial(context.fromMaterial)
        appendLine()
        appendLine("=== TO 브랜치: ${context.toMaterial.branchName} ===")
        appendBranchMaterial(context.toMaterial)
    }

    private fun buildDeepPrompt(context: MergeContext, userNote: String?): String = buildString {
        appendLine("Git 기반 AI 대화 시스템에서 두 브랜치를 Deep Merge(지능형 통합) 합니다.")
        appendLine("아래 재료를 꼼꼼히 분석하여 두 브랜치의 내용을 지능적으로 통합한 병합 커밋 요약을 생성하세요.")
        appendLine("반드시 JSON만 출력하세요. (설명/마크다운 금지)")
        appendLine()
        appendLine("출력 JSON 스키마:")
        appendLine("""{ "keyPoint": string, "shortSummary": string, "longSummary": string }""")
        appendLine()
        appendLine("작성 규칙:")
        appendLine("- keyPoint: 10~60자 한국어 제목 (통합 결과 요지)")
        appendLine("- shortSummary: 3~5문장으로 통합된 결과 요약")
        appendLine("- longSummary: 10~25줄로 아래 구조를 따를 것:")
        appendLine("  1) [통합 타임라인]: 양쪽 브랜치의 주요 사건을 시간순으로 정리")
        appendLine("  2) [겹치는 내용]: 두 브랜치에서 동일하게 논의된 부분 요약")
        appendLine("  3) [보강 내용]: 한쪽에만 있어 다른 쪽을 보완하는 정보")
        appendLine("  4) [충돌/차이점]: 서로 다른 결론이나 설계가 있는 경우 명시 (없으면 '충돌 없음')")
        appendLine("  5) [최종 결론]: 통합 결과 요약")
        appendLine("- 사실만 기반으로 작성할 것")
        appendLine("- 어떤 브랜치에서 어떤 판단을 했는지 출처를 명시할 것")

        if (!userNote.isNullOrBlank()) {
            appendLine()
            appendLine("[사용자 특별 지시사항]")
            appendLine(userNote)
        }

        appendLine()
        appendLine("=== FROM 브랜치: ${context.fromMaterial.branchName} ===")
        appendBranchMaterial(context.fromMaterial)
        appendLine()
        appendLine("=== TO 브랜치: ${context.toMaterial.branchName} ===")
        appendBranchMaterial(context.toMaterial)
    }

    private fun StringBuilder.appendBranchMaterial(material: com.gait.gaitproject.service.workspace.BranchMaterial) {
        if (material.shortSummaries.isNotBlank()) {
            appendLine("[커밋 요약]")
            appendLine(material.shortSummaries)
        }
        if (material.longSummaries.isNotBlank()) {
            appendLine("[상세 요약]")
            appendLine(material.longSummaries)
        }
        if (material.rawMessageExcerpt.isNotBlank()) {
            appendLine("[원본 대화 발췌]")
            appendLine(material.rawMessageExcerpt)
        }
    }

    private fun callAi(prompt: String, options: OpenAiChatOptions, mode: String): AiMergeSummary {
        return try {
            val response = chatModel.call(Prompt(prompt, options))
            val json = response.result.output.text
            objectMapper.readValue(json, AiMergeSummary::class.java)
        } catch (e: Exception) {
            logger.warn("$mode merge AI failed. Fallback to empty summary. reason={}", e.message)
            AiMergeSummary(
                keyPoint = "Merged Branches",
                shortSummary = "AI 요약 생성에 실패하여 기본 메시지로 병합되었습니다.",
                longSummary = "AI 병합 실패. 수동 확인 필요."
            )
        }
    }

    private fun jsonResponseFormat(): ResponseFormat =
        ResponseFormat().apply { type = ResponseFormat.Type.JSON_OBJECT }
}
