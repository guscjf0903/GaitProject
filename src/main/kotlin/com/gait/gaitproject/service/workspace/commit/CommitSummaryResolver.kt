package com.gait.gaitproject.service.workspace.commit

import com.gait.gaitproject.domain.chat.entity.Message
import com.gait.gaitproject.dto.workspace.CommitCreateRequest
import com.gait.gaitproject.service.ai.CommitSummaryAiService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

data class ResolvedCommitSummary(
    val keyPoint: String,
    val shortSummary: String?,
    val longSummary: String?,
)

@Service
class CommitSummaryResolver(
    @Autowired(required = false) private val commitSummaryAiService: CommitSummaryAiService?,
) {
    fun resolve(
        request: CommitCreateRequest,
        pendingMessages: List<Message>,
        parentLongSummary: String?,
    ): ResolvedCommitSummary {
        val needsSummary =
            request.shortSummary.isNullOrBlank() ||
                request.longSummary.isNullOrBlank() ||
                request.keyPoint == "AUTO_COMMIT"

        val aiSummary = if (needsSummary) {
            commitSummaryAiService?.summarize(pendingMessages, parentLongSummary)
        } else {
            null
        }

        val finalKeyPoint = when {
            request.keyPoint == "AUTO_COMMIT" && !aiSummary?.keyPoint.isNullOrBlank() -> aiSummary!!.keyPoint!!.trim()
            request.keyPoint.isNotBlank() -> request.keyPoint.trim()
            else -> "COMMIT"
        }
        val finalShortSummary = request.shortSummary?.takeIf { it.isNotBlank() } ?: aiSummary?.shortSummary
        val finalLongSummary = request.longSummary?.takeIf { it.isNotBlank() } ?: aiSummary?.longSummary

        return ResolvedCommitSummary(
            keyPoint = finalKeyPoint,
            shortSummary = finalShortSummary,
            longSummary = finalLongSummary,
        )
    }
}
