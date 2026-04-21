package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.workspace.entity.Workspace
import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.CommitRepository
import com.gait.gaitproject.domain.workspace.repository.WorkspaceRepository
import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.dto.workspace.WorkspaceCreateRequest
import com.gait.gaitproject.dto.workspace.WorkspaceResponse
import com.gait.gaitproject.service.access.AccessGuard
import com.gait.gaitproject.service.common.NotFoundException
import com.gait.gaitproject.service.user.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class WorkspaceService(
    private val workspaceRepository: WorkspaceRepository,
    private val branchRepository: BranchRepository,
    private val commitRepository: CommitRepository,
    private val accessGuard: AccessGuard,
    private val userService: UserService
) {
    fun get(workspaceId: UUID): Workspace =
        workspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Workspace not found. id=$workspaceId")
        }

    fun getOwnedResponse(workspaceId: UUID, authenticatedUserId: UUID): WorkspaceResponse =
        WorkspaceResponse.fromEntity(accessGuard.requireWorkspaceOwner(workspaceId, authenticatedUserId).workspace)

    fun listByUser(requestedUserId: UUID, authenticatedUserId: UUID): List<WorkspaceResponse> {
        accessGuard.requireSelf(requestedUserId, authenticatedUserId)
        return workspaceRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(authenticatedUserId)
            .map(WorkspaceResponse::fromEntity)
    }

    @Transactional
    fun create(request: WorkspaceCreateRequest, authenticatedUserId: UUID): WorkspaceResponse {
        accessGuard.requireSelf(requireNotNull(request.userId), authenticatedUserId)
        val user = userService.get(authenticatedUserId)
        val entity = request.toEntity(user)
        val savedWorkspace = workspaceRepository.save(entity)

        // ✅ Workspace 생성 시 기본 브랜치(main) 자동 생성
        // - 프론트에서 workspace 클릭 후 "다음 단계"가 비어 보이지 않게 하기 위함
        val main = branchRepository.save(
            Branch(
                workspace = savedWorkspace,
                name = "main",
                description = "기본 브랜치",
                isDefault = true
            )
        )

        // ✅ 초기 커밋 1개 생성 (initProject)
        // - 프론트가 새로고침 시 그래프를 서버 커밋 목록으로 복원할 수 있게
        // - 프로젝트 시작점을 명확히 하기 위함
        val initCommit = commitRepository.save(
            Commit(
                workspace = savedWorkspace,
                branch = main,
                parent = null,
                createdByUser = null,
                keyPoint = "initProject",
                shortSummary = null,
                longSummary = null,
                mergeType = MergeType.NONE,
                isMerge = false
            )
        )
        main.baseCommit = initCommit
        main.headCommit = initCommit
        branchRepository.save(main)

        savedWorkspace.defaultBranch = main
        val updated = workspaceRepository.save(savedWorkspace)

        return WorkspaceResponse.fromEntity(updated)
    }
}

