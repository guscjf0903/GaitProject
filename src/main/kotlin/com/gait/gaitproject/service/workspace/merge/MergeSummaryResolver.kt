package com.gait.gaitproject.service.workspace.merge

import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.service.ai.MergeSummaryAiService
import com.gait.gaitproject.service.workspace.MergeContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

data class ResolvedMergeSummary(
    val keyPoint: String,
    val shortSummary: String?,
    val longSummary: String?,
)

@Service
class MergeSummaryResolver(
    @Autowired(required = false) private val mergeSummaryAiService: MergeSummaryAiService?,
) {
    fun resolve(mergeType: MergeType, context: MergeContext, userNote: String?): ResolvedMergeSummary {
        val aiSummary = when (mergeType) {
            MergeType.SQUASH -> mergeSummaryAiService?.summarizeSquash(context, userNote)
            MergeType.DEEP -> mergeSummaryAiService?.summarizeDeep(context, userNote)
            else -> null
        }

        return when (mergeType) {
            MergeType.SQUASH -> ResolvedMergeSummary(
                keyPoint = aiSummary?.keyPoint ?: "Merged from ${context.fromMaterial.branchName}",
                shortSummary = aiSummary?.shortSummary ?: buildFallbackShortSummary(context),
                longSummary = aiSummary?.longSummary ?: buildFallbackLongSummary(context),
            )
            MergeType.DEEP -> ResolvedMergeSummary(
                keyPoint = aiSummary?.keyPoint ?: "Deep Merged from ${context.fromMaterial.branchName}",
                shortSummary = aiSummary?.shortSummary,
                longSummary = aiSummary?.longSummary,
            )
            else -> throw IllegalArgumentException("지원되지 않는 머지 타입입니다: $mergeType")
        }
    }

    private fun buildFallbackShortSummary(context: MergeContext): String {
        val fromShort = context.fromMaterial.shortSummaries
        return if (fromShort.isNotBlank()) {
            "Squash merged:\n$fromShort"
        } else {
            "Squash merged from ${context.fromMaterial.branchName}"
        }
    }

    private fun buildFallbackLongSummary(context: MergeContext): String =
        buildString {
            appendLine("[${context.fromMaterial.branchName} → ${context.toMaterial.branchName} Squash Merge]")

            if (context.fromMaterial.shortSummaries.isNotBlank()) {
                appendLine()
                appendLine("--- 주요 변경 사항 ---")
                appendLine(context.fromMaterial.shortSummaries)
            }

            if (context.fromMaterial.longSummaries.isNotBlank()) {
                appendLine()
                appendLine("--- 상세 내용 ---")
                appendLine(context.fromMaterial.longSummaries)
            }
        }.trim()
}
