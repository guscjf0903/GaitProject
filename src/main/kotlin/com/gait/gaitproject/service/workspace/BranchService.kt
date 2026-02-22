package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.CommitRepository
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
    private val workspaceRepository: WorkspaceRepository,
    private val commitRepository: CommitRepository
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

        branchRepository.findByWorkspace_IdAndNameAndDeletedAtIsNull(workspaceId, request.name)?.let {
            throw IllegalArgumentException("이미 존재하는 브랜치입니다. name=${request.name}")
        }

        if (request.isDefault) {
            val existingDefault = branchRepository.findByWorkspace_IdAndIsDefaultTrueAndDeletedAtIsNull(workspace.id!!)
            existingDefault?.let {
                it.isDefault = false
                branchRepository.save(it)
            }
        }

        val selectedBaseCommit = request.baseCommitId?.let { commitId ->
            commitRepository.findById(commitId).orElseThrow {
                NotFoundException("Commit not found. id=$commitId")
            }.also { commit ->
                if (commit.workspace.id != workspace.id) {
                    throw IllegalArgumentException(
                        "Base commit does not belong to workspace. commitId=${commit.id}, workspaceId=${workspace.id}"
                    )
                }
            }
        }

        val entity = request.toEntity(workspace).apply {
            // 과거 커밋에서 브랜치를 따는 경우, 시작점(base)만 기록하고
            // HEAD는 "브랜치 시작 커밋"을 별도로 만들어 분기선을 명확히 합니다.
            baseCommit = selectedBaseCommit
            headCommit = null
        }

        val saved = branchRepository.save(entity)

        if (selectedBaseCommit != null) {
            val startCommit = commitRepository.save(
                Commit(
                    workspace = workspace,
                    branch = saved,
                    parent = selectedBaseCommit,
                    createdByUser = null,
                    keyPoint = "Start ${saved.name}",
                    shortSummary = null,
                    longSummary = null,
                    mergeType = MergeType.NONE,
                    isMerge = false
                )
            )
            saved.headCommit = startCommit
            branchRepository.save(saved)
        }

        if (saved.isDefault) {
            workspace.defaultBranch = saved
            workspaceRepository.save(workspace)
        }

        return BranchResponse.Companion.fromEntity(saved)
    }
}


