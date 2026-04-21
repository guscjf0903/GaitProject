package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.service.workspace.merge.MergeMaterialAssembler
import com.gait.gaitproject.service.workspace.merge.MergePathResolver
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
    private val mergePathResolver: MergePathResolver,
    private val mergeMaterialAssembler: MergeMaterialAssembler,
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

        val mergePaths = mergePathResolver.resolve(
            fromHead = fromBranch.headCommit,
            toHead = toBranch.headCommit,
        )
        val fromMaterial = mergeMaterialAssembler.assemble(fromBranch.name, mergePaths.fromPath, workspaceId, budget)
        val toMaterial = mergeMaterialAssembler.assemble(toBranch.name, mergePaths.toPath, workspaceId, budget)

        return MergeContext(
            fromMaterial = fromMaterial,
            toMaterial = toMaterial,
            commonAncestor = mergePaths.commonAncestor,
            fromPath = mergePaths.fromPath,
            toPath = mergePaths.toPath
        )
    }
}
