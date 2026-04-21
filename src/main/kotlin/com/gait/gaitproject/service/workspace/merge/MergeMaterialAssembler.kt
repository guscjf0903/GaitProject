package com.gait.gaitproject.service.workspace.merge

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.service.chat.TokenEstimator
import com.gait.gaitproject.service.workspace.BranchMaterial
import com.gait.gaitproject.service.workspace.MergeBudget
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MergeMaterialAssembler(
    private val messageRepository: MessageRepository,
) {
    fun assemble(
        branchName: String,
        path: List<Commit>,
        workspaceId: UUID,
        budget: MergeBudget,
    ): BranchMaterial {
        if (path.isEmpty()) {
            return BranchMaterial(branchName, "", "", "")
        }

        val commitIds = path.mapNotNull { it.id }
        val rawMessages = if (commitIds.isNotEmpty()) {
            messageRepository.findByWorkspace_IdAndCommit_IdInAndDeletedAtIsNull(workspaceId, commitIds)
                .sortedWith(compareBy({ it.createdAt }, { it.sequence }))
        } else {
            emptyList()
        }
        val rawMessageExcerpt = trimToBudget(
            rawMessages.map { "${it.role}: ${it.content}" },
            budget.rawMessageBudget,
        )

        val shortSummaries = trimToBudget(
            path.mapNotNull { it.shortSummary ?: it.keyPoint },
            budget.shortSummaryBudget,
        )

        val longSummaries = trimToBudget(
            path.mapNotNull { it.longSummary },
            budget.longSummaryBudget,
        )

        return BranchMaterial(
            branchName = branchName,
            rawMessageExcerpt = rawMessageExcerpt,
            shortSummaries = shortSummaries,
            longSummaries = longSummaries,
        )
    }

    private fun trimToBudget(items: List<String>, budgetTokens: Int): String {
        if (items.isEmpty() || budgetTokens <= 0) return ""

        val selected = mutableListOf<String>()
        var usedTokens = 0
        for (item in items.asReversed()) {
            val tokens = TokenEstimator.estimate(item)
            if (usedTokens + tokens > budgetTokens && selected.isNotEmpty()) break
            selected.add(item)
            usedTokens += tokens
        }

        selected.reverse()
        return selected.joinToString("\n")
    }
}
