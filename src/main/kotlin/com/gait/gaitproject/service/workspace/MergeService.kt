package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.common.enums.PlanType
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.CommitRepository
import com.gait.gaitproject.domain.workspace.repository.MergeRepository
import com.gait.gaitproject.domain.workspace.repository.WorkspaceRepository
import com.gait.gaitproject.dto.workspace.MergeCreateRequest
import com.gait.gaitproject.dto.workspace.MergeResponse
import com.gait.gaitproject.service.ai.MergeSummaryAiService
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MergeService(
    private val mergeRepository: MergeRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val branchRepository: BranchRepository,
    private val commitRepository: CommitRepository,
    private val userRepository: UserRepository,
    private val mergeContextBuilder: MergeContextBuilder,
    @Autowired(required = false) private val mergeSummaryAiService: MergeSummaryAiService?
) {
    @Transactional
    fun create(request: MergeCreateRequest, initiatedByUserId: UUID? = null): MergeResponse {
        val workspaceId = requireNotNull(request.workspaceId)
        val fromBranchId = requireNotNull(request.fromBranchId)
        val toBranchId = requireNotNull(request.toBranchId)

        val workspace = workspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Workspace not found. id=$workspaceId")
        }
        val fromBranch = branchRepository.findById(fromBranchId).orElseThrow {
            NotFoundException("Branch not found. id=$fromBranchId")
        }
        val toBranch = branchRepository.findById(toBranchId).orElseThrow {
            NotFoundException("Branch not found. id=$toBranchId")
        }

        if (fromBranch.workspace.id != workspace.id || toBranch.workspace.id != workspace.id) {
            throw IllegalArgumentException("Branches must belong to workspace. workspaceId=${workspace.id}")
        }

        val initiatedByUser = initiatedByUserId?.let { userId ->
            userRepository.findById(userId).orElseThrow { NotFoundException("User not found. id=$userId") }
        }

        val userPlan = initiatedByUser?.plan ?: PlanType.FREE
        val reqMergeType = request.mergeType ?: MergeType.SQUASH

        validateMergePolicy(userPlan, reqMergeType)

        val context = mergeContextBuilder.build(fromBranch, toBranch, workspaceId, reqMergeType)

        if (context.fromPath.isEmpty()) {
            throw IllegalArgumentException("이미 최신 상태입니다. 병합할 새로운 커밋이 없습니다.")
        }

        val finalMergeCommit: Commit = when (reqMergeType) {
            MergeType.SQUASH -> {
                val aiSummary = mergeSummaryAiService?.summarizeSquash(context, request.notes)

                val mergeCommit = Commit(
                    workspace = workspace,
                    branch = toBranch,
                    parent = toBranch.headCommit,
                    mergeParent = fromBranch.headCommit,
                    mergeType = MergeType.SQUASH,
                    isMerge = true,
                    createdByUser = initiatedByUser,
                    keyPoint = aiSummary?.keyPoint ?: "Merged from ${fromBranch.name}",
                    shortSummary = aiSummary?.shortSummary ?: buildFallbackShortSummary(context),
                    longSummary = aiSummary?.longSummary ?: buildFallbackLongSummary(context)
                )
                commitRepository.save(mergeCommit)
            }
            MergeType.DEEP -> {
                val aiSummary = mergeSummaryAiService?.summarizeDeep(context, request.notes)

                val mergeCommit = Commit(
                    workspace = workspace,
                    branch = toBranch,
                    parent = toBranch.headCommit,
                    mergeParent = fromBranch.headCommit,
                    mergeType = MergeType.DEEP,
                    isMerge = true,
                    createdByUser = initiatedByUser,
                    keyPoint = aiSummary?.keyPoint ?: "Deep Merged from ${fromBranch.name}",
                    shortSummary = aiSummary?.shortSummary,
                    longSummary = aiSummary?.longSummary
                )
                commitRepository.save(mergeCommit)
            }
            else -> throw IllegalArgumentException("지원되지 않는 머지 타입입니다: $reqMergeType")
        }

        toBranch.headCommit = finalMergeCommit
        branchRepository.save(toBranch)

        val entity = request.toEntity(
            workspace = workspace,
            fromBranch = fromBranch,
            toBranch = toBranch,
            fromCommit = fromBranch.headCommit,
            toCommit = toBranch.headCommit,
            mergeCommit = finalMergeCommit,
            initiatedByUser = initiatedByUser
        )

        val saved = mergeRepository.save(entity)
        return MergeResponse.fromEntity(saved, fromBranch.name, toBranch.name)
    }

    private fun validateMergePolicy(userPlan: PlanType, mergeType: MergeType) {
        if (userPlan == PlanType.FREE) {
            throw IllegalArgumentException("Free 플랜에서는 머지 기능을 사용할 수 없습니다. 플랜을 업그레이드해 주세요.")
        }
        when (mergeType) {
            MergeType.SQUASH -> {
                // STANDARD, MASTER 모두 허용
            }
            MergeType.DEEP -> {
                if (userPlan == PlanType.STANDARD) {
                    throw IllegalArgumentException("Deep Merge는 Master 플랜 전용 기능입니다. 플랜을 업그레이드해 주세요.")
                }
            }
            MergeType.FAST_FORWARD -> {
                throw IllegalArgumentException("Fast-Forward 머지는 더 이상 지원되지 않습니다. Squash 또는 Deep Merge를 사용해 주세요.")
            }
            MergeType.NONE -> {
                throw IllegalArgumentException("머지 타입을 선택해 주세요.")
            }
        }
    }

    private fun buildFallbackShortSummary(context: MergeContext): String {
        val fromShort = context.fromMaterial.shortSummaries
        return if (fromShort.isNotBlank()) "Squash merged:\n$fromShort" else "Squash merged from ${context.fromMaterial.branchName}"
    }

    private fun buildFallbackLongSummary(context: MergeContext): String {
        val fromShort = context.fromMaterial.shortSummaries
        val fromLong = context.fromMaterial.longSummaries
        return buildString {
            appendLine("[${context.fromMaterial.branchName} → ${context.toMaterial.branchName} Squash Merge]")
            if (fromShort.isNotBlank()) {
                appendLine()
                appendLine("--- 주요 변경 사항 ---")
                appendLine(fromShort)
            }
            if (fromLong.isNotBlank()) {
                appendLine()
                appendLine("--- 상세 내용 ---")
                appendLine(fromLong)
            }
        }.trim()
    }
}
