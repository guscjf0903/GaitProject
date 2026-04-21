package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.common.enums.PlanType
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.CommitRepository
import com.gait.gaitproject.domain.workspace.repository.MergeRepository
import com.gait.gaitproject.dto.workspace.MergeCreateRequest
import com.gait.gaitproject.dto.workspace.MergeResponse
import com.gait.gaitproject.service.access.AccessGuard
import com.gait.gaitproject.service.common.NotFoundException
import com.gait.gaitproject.service.workspace.merge.MergePolicyValidator
import com.gait.gaitproject.service.workspace.merge.MergeSummaryResolver
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MergeService(
    private val mergeRepository: MergeRepository,
    private val branchRepository: BranchRepository,
    private val commitRepository: CommitRepository,
    private val userRepository: UserRepository,
    private val accessGuard: AccessGuard,
    private val mergeContextBuilder: MergeContextBuilder,
    private val mergePolicyValidator: MergePolicyValidator,
    private val mergeSummaryResolver: MergeSummaryResolver,
) {
    @Transactional
    fun create(request: MergeCreateRequest, authenticatedUserId: UUID): MergeResponse {
        val workspaceId = requireNotNull(request.workspaceId)
        val fromBranchId = requireNotNull(request.fromBranchId)
        val toBranchId = requireNotNull(request.toBranchId)

        val fromAccess = accessGuard.requireBranchAccess(workspaceId, fromBranchId, authenticatedUserId)
        val toAccess = accessGuard.requireBranchAccess(workspaceId, toBranchId, authenticatedUserId)
        val workspace = fromAccess.workspace
        val fromBranch = fromAccess.branch
        val toBranch = toAccess.branch

        val initiatedByUser = userRepository.findById(authenticatedUserId).orElseThrow {
            NotFoundException("User not found. id=$authenticatedUserId")
        }

        val userPlan = initiatedByUser.plan
        val reqMergeType = request.mergeType ?: MergeType.SQUASH

        mergePolicyValidator.validate(userPlan, reqMergeType)

        val context = mergeContextBuilder.build(fromBranch, toBranch, workspaceId, reqMergeType)

        if (context.fromPath.isEmpty()) {
            throw IllegalArgumentException("이미 최신 상태입니다. 병합할 새로운 커밋이 없습니다.")
        }

        val resolvedSummary = mergeSummaryResolver.resolve(reqMergeType, context, request.notes)
        val fromHeadBeforeMerge = fromBranch.headCommit
        val toHeadBeforeMerge = toBranch.headCommit
        val finalMergeCommit: Commit = commitRepository.save(
            Commit(
                workspace = workspace,
                branch = toBranch,
                parent = toHeadBeforeMerge,
                mergeParent = fromHeadBeforeMerge,
                mergeType = reqMergeType,
                isMerge = true,
                createdByUser = initiatedByUser,
                keyPoint = resolvedSummary.keyPoint,
                shortSummary = resolvedSummary.shortSummary,
                longSummary = resolvedSummary.longSummary,
            )
        )

        toBranch.headCommit = finalMergeCommit
        branchRepository.save(toBranch)

        val entity = request.toEntity(
            workspace = workspace,
            fromBranch = fromBranch,
            toBranch = toBranch,
            fromCommit = fromHeadBeforeMerge,
            toCommit = toHeadBeforeMerge,
            mergeCommit = finalMergeCommit,
            initiatedByUser = initiatedByUser
        )

        val saved = mergeRepository.save(entity)
        return MergeResponse.fromEntity(saved, fromBranch.name, toBranch.name)
    }
}
