package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.WorkspaceRepository
import com.gait.gaitproject.dto.workspace.BranchCreateRequest
import com.gait.gaitproject.dto.workspace.BranchResponse
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class BranchService(
    private val branchRepository: BranchRepository,
    private val workspaceRepository: WorkspaceRepository
) {
    fun get(branchId: UUID): Branch =
        branchRepository.findById(branchId).orElseThrow {
            NotFoundException("Branch not found. id=$branchId")
        }

    fun listByWorkspace(workspaceId: UUID): List<BranchResponse> =
        branchRepository.findByWorkspace_IdAndDeletedAtIsNullOrderByCreatedAtAsc(workspaceId)
            .map(BranchResponse.Companion::fromEntity)

    @Transactional 
    fun create(request: BranchCreateRequest): BranchResponse {
        val workspaceId = requireNotNull(request.workspaceId)
        val workspace = workspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Workspace not found. id=$workspaceId")
        }

        if (request.isDefault) {
            val existingDefault = branchRepository.findByWorkspace_IdAndIsDefaultTrueAndDeletedAtIsNull(workspace.id!!)
            existingDefault?.let {
                it.isDefault = false
                branchRepository.save(it)
            }
        }

        val saved = branchRepository.save(request.toEntity(workspace))

        if (saved.isDefault) {
            workspace.defaultBranch = saved
            workspaceRepository.save(workspace)
        }

        return BranchResponse.Companion.fromEntity(saved)
    }
}


