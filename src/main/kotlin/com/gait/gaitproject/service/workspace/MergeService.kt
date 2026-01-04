package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.MergeRepository
import com.gait.gaitproject.domain.workspace.repository.WorkspaceRepository
import com.gait.gaitproject.dto.workspace.MergeCreateRequest
import com.gait.gaitproject.dto.workspace.MergeResponse
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MergeService(
    private val mergeRepository: MergeRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val branchRepository: BranchRepository,
    private val userRepository: UserRepository
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

        val entity = request.toEntity(
            workspace = workspace,
            fromBranch = fromBranch,
            toBranch = toBranch,
            fromCommit = fromBranch.headCommit,
            toCommit = toBranch.headCommit,
            mergeCommit = null,
            initiatedByUser = initiatedByUser
        )

        val saved = mergeRepository.save(entity)

        // Standard 플랜 수준의 Fast-forward를 최소 구현 (충돌 개념 없이 head만 이동)
        if (request.mergeType?.name == "FAST_FORWARD") {
            toBranch.headCommit = fromBranch.headCommit
            branchRepository.save(toBranch)
        }

        return MergeResponse.fromEntity(saved)
    }
}


