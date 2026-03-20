package com.gait.gaitproject.service.rag

import com.gait.gaitproject.domain.workspace.repository.CommitVectorSearchResult
import com.gait.gaitproject.service.chat.TokenEstimator

object RagContextFormatter {

    fun format(results: List<CommitVectorSearchResult>, tokenBudget: Int): String {
        if (results.isEmpty()) return ""

        val sb = StringBuilder()
        var usedTokens = 0

        for (item in results) {
            val relevance = "%.3f".format(1.0 - item.distance)
            val block = buildString {
                appendLine("--- Retrieved Commit (relevance: $relevance) ---")
                item.keyPoint?.let { appendLine("KeyPoint: $it") }
                item.shortSummary?.let { appendLine("Summary: $it") }
                item.longSummary?.let { appendLine("Detail: $it") }
            }

            val tokens = TokenEstimator.estimate(block)
            if (usedTokens + tokens > tokenBudget) break

            sb.appendLine(block)
            usedTokens += tokens
        }

        return sb.toString().trimEnd()
    }
}
