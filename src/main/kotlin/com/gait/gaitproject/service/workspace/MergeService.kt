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
        val reqMergeType = request.mergeType ?: MergeType.FAST_FORWARD

        // 권한 체크
        if (userPlan == PlanType.FREE) {
            throw IllegalArgumentException("Free plan cannot use Merge feature.")
        }
        if (reqMergeType == MergeType.DEEP && userPlan !in listOf(PlanType.STANDARD, PlanType.MASTER)) {
             // Standard 도 제한적 허용이거나 기획상 Master/Pro만 허용. 
             // 기획서 4.5: Master 플랜은 지능형 Deep Merge. Standard는 단순/FastForward.
             if (userPlan == PlanType.STANDARD) {
                 throw IllegalArgumentException("Standard plan cannot use DEEP merge. Please upgrade to Master plan.")
             }
        }

        // 공통 조상 찾기 (단순화된 방식: toBranch 조상들을 Set에 넣고 fromBranch를 거슬러 올라감)
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

        fromPath.reverse() // 오래된 것부터 최신순

        var finalMergeCommit: Commit? = null

        when (reqMergeType) {
            MergeType.FAST_FORWARD -> {
                toBranch.headCommit = fromBranch.headCommit
            }
            MergeType.SQUASH -> {
                if (fromPath.isEmpty()) throw IllegalArgumentException("Already up to date.")
                
                val combinedShort = fromPath.mapNotNull { it.shortSummary ?: it.keyPoint }.joinToString("\n- ")
                
                val mergeCommit = Commit(
                    workspace = workspace,
                    branch = toBranch,
                    parent = toBranch.headCommit,
                    mergeParent = fromBranch.headCommit,
                    mergeType = MergeType.SQUASH,
                    isMerge = true,
                    createdByUser = initiatedByUser,
                    keyPoint = "Merged from ${fromBranch.name}",
                    shortSummary = "Squash merged from ${fromBranch.name}",
                    longSummary = "- $combinedShort"
                )
                finalMergeCommit = commitRepository.save(mergeCommit)
                toBranch.headCommit = finalMergeCommit
            }
            MergeType.DEEP -> {
                if (fromPath.isEmpty()) throw IllegalArgumentException("Already up to date.")
                
                // toBranch의 조상들도 공통 조상 이후부터 가져옴
                val toPath = mutableListOf<Commit>()
                var cur: Commit? = toBranch.headCommit
                while (cur != null && cur.id != commonAncestor?.id) {
                    toPath.add(cur)
                    cur = cur.parent
                }
                toPath.reverse()

                val fromSummaries = fromPath.mapNotNull { it.longSummary ?: it.shortSummary ?: it.keyPoint }
                val toSummaries = toPath.mapNotNull { it.longSummary ?: it.shortSummary ?: it.keyPoint }

                val aiSummary = mergeSummaryAiService?.summarizeMerge(
                    baseSummaries = toSummaries,
                    targetSummaries = fromSummaries,
                    userNote = request.notes
                )

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
                finalMergeCommit = commitRepository.save(mergeCommit)
                toBranch.headCommit = finalMergeCommit
            }
            else -> throw IllegalArgumentException("Unsupported merge type: $reqMergeType")
        }

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
        return MergeResponse.fromEntity(saved)
    }
}


