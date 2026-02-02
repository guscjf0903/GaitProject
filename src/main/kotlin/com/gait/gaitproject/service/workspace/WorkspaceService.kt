package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.workspace.entity.Workspace
import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.WorkspaceRepository
import com.gait.gaitproject.dto.workspace.WorkspaceCreateRequest
import com.gait.gaitproject.dto.workspace.WorkspaceResponse
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
    private val userService: UserService
) {
    fun get(workspaceId: UUID): Workspace =
        workspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Workspace not found. id=$workspaceId")
        }

    fun getResponse(workspaceId: UUID): WorkspaceResponse =
        WorkspaceResponse.fromEntity(get(workspaceId))

    fun listByUser(userId: UUID): List<WorkspaceResponse> =
        workspaceRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId)
            .map(WorkspaceResponse::fromEntity)

    @Transactional
    fun create(request: WorkspaceCreateRequest): WorkspaceResponse {
        val user = userService.get(requireNotNull(request.userId))
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
        savedWorkspace.defaultBranch = main
        val updated = workspaceRepository.save(savedWorkspace)

        return WorkspaceResponse.fromEntity(updated)
    }
}


