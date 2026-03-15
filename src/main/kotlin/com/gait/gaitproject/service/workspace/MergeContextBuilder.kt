package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.service.chat.TokenEstimator
import org.springframework.stereotype.Component
import java.util.UUID

data class MergeBudget(
    val totalInputTokens: Int,
    val rawMessagePercent: Int,
    val shortSummaryPercent: Int,
    val longSummaryPercent: Int
) {
    val rawMessageBudget get() = totalInputTokens * rawMessagePercent / 100
    val shortSummaryBudget get() = totalInputTokens * shortSummaryPercent / 100
    val longSummaryBudget get() = totalInputTokens * longSummaryPercent / 100

    companion object {
        val SQUASH = MergeBudget(totalInputTokens = 2000, rawMessagePercent = 20, shortSummaryPercent = 50, longSummaryPercent = 30)
        val DEEP = MergeBudget(totalInputTokens = 6000, rawMessagePercent = 40, shortSummaryPercent = 20, longSummaryPercent = 40)
    }
}

data class BranchMaterial(
    val branchName: String,
    val rawMessageExcerpt: String,
    val shortSummaries: String,
    val longSummaries: String
)

data class MergeContext(
    val fromMaterial: BranchMaterial,
    val toMaterial: BranchMaterial,
    val commonAncestor: Commit?,
    val fromPath: List<Commit>,
    val toPath: List<Commit>
)

@Component
class MergeContextBuilder(
    private val messageRepository: MessageRepository
) {
    fun build(
        fromBranch: Branch,
        toBranch: Branch,
        workspaceId: UUID,
        mergeType: MergeType
    ): MergeContext {
        val budget = when (mergeType) {
            MergeType.SQUASH -> MergeBudget.SQUASH
            MergeType.DEEP -> MergeBudget.DEEP
            else -> MergeBudget.SQUASH
        }

        val toAncestors = mutableSetOf<UUID>()
        var curTo: Commit? = toBranch.headCommit
        while (curTo != null) {
            toAncestors.add(curTo.id!!)
            curTo = curTo.parent
        }

        var commonAncestor: Commit? = null
        val fromPath = mutableListOf<Commit>()
        var curFrom: Commit? = fromBranch.headCommit
        while (curFrom != null) {
            if (toAncestors.contains(curFrom.id!!)) {
                commonAncestor = curFrom
                break
            }
            fromPath.add(curFrom)
            curFrom = curFrom.parent
        }
        fromPath.reverse()

        val toPath = mutableListOf<Commit>()
        var cur: Commit? = toBranch.headCommit
        while (cur != null && cur.id != commonAncestor?.id) {
            toPath.add(cur)
            cur = cur.parent
        }
        toPath.reverse()

        val fromMaterial = buildBranchMaterial(fromBranch.name, fromPath, workspaceId, budget)
        val toMaterial = buildBranchMaterial(toBranch.name, toPath, workspaceId, budget)

        return MergeContext(
            fromMaterial = fromMaterial,
            toMaterial = toMaterial,
            commonAncestor = commonAncestor,
            fromPath = fromPath,
            toPath = toPath
        )
    }

    private fun buildBranchMaterial(
        branchName: String,
        path: List<Commit>,
        workspaceId: UUID,
        budget: MergeBudget
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
            budget.rawMessageBudget
        )

        val shorts = path.mapNotNull { it.shortSummary ?: it.keyPoint }
        val shortSummaries = trimToBudget(shorts, budget.shortSummaryBudget)

        val longs = path.mapNotNull { it.longSummary }
        val longSummaries = trimToBudget(longs, budget.longSummaryBudget)

        return BranchMaterial(branchName, rawMessageExcerpt, shortSummaries, longSummaries)
    }

    /**
     * Prioritizes recent items when budget is limited; returns result in chronological order.
     */
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
