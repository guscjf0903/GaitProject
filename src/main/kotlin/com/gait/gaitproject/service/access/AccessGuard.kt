package com.gait.gaitproject.service.access

import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.entity.Workspace
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.CommitRepository
import com.gait.gaitproject.domain.workspace.repository.WorkspaceRepository
import com.gait.gaitproject.service.common.ForbiddenException
import com.gait.gaitproject.service.common.NotFoundException
import com.gait.gaitproject.service.common.UnauthorizedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class WorkspaceAccess(
    val authenticatedUserId: UUID,
    val workspace: Workspace,
)

data class BranchAccess(
    val authenticatedUserId: UUID,
    val workspace: Workspace,
    val branch: Branch,
)

data class CommitAccess(
    val authenticatedUserId: UUID,
    val workspace: Workspace,
    val branch: Branch,
    val commit: Commit,
)

@Service
@Transactional(readOnly = true)
class AccessGuard(
    private val workspaceRepository: WorkspaceRepository,
    private val branchRepository: BranchRepository,
    private val commitRepository: CommitRepository,
) {
    fun requireAuthenticated(authenticatedUserId: UUID?): UUID =
        authenticatedUserId ?: throw UnauthorizedException("인증이 필요합니다.")

    fun requireSelf(requestedUserId: UUID, authenticatedUserId: UUID) {
        if (requestedUserId != authenticatedUserId) {
            throw ForbiddenException("다른 사용자의 리소스에는 접근할 수 없습니다.")
        }
    }

    fun requireWorkspaceOwner(workspaceId: UUID, authenticatedUserId: UUID): WorkspaceAccess {
        val workspace = workspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Workspace not found. id=$workspaceId")
        }
        if (workspace.user.id != authenticatedUserId) {
            throw ForbiddenException("해당 워크스페이스에 접근할 수 없습니다. workspaceId=$workspaceId")
        }
        return WorkspaceAccess(authenticatedUserId = authenticatedUserId, workspace = workspace)
    }

    fun requireBranchAccess(workspaceId: UUID, branchId: UUID, authenticatedUserId: UUID): BranchAccess {
        val workspaceAccess = requireWorkspaceOwner(workspaceId, authenticatedUserId)
        val branch = branchRepository.findById(branchId).orElseThrow {
            NotFoundException("Branch not found. id=$branchId")
        }
        if (branch.workspace.id != workspaceAccess.workspace.id) {
            throw IllegalArgumentException(
                "Branch does not belong to workspace. branchId=${branch.id}, workspaceId=${workspaceAccess.workspace.id}",
            )
        }
        return BranchAccess(
            authenticatedUserId = authenticatedUserId,
            workspace = workspaceAccess.workspace,
            branch = branch,
        )
    }

    fun requireCommitAccess(workspaceId: UUID, branchId: UUID, commitId: UUID, authenticatedUserId: UUID): CommitAccess {
        val branchAccess = requireBranchAccess(workspaceId, branchId, authenticatedUserId)
        val commit = commitRepository.findById(commitId).orElseThrow {
            NotFoundException("Commit not found. id=$commitId")
        }
        if (commit.branch.id != branchAccess.branch.id) {
            throw IllegalArgumentException(
                "Commit does not belong to branch. commitId=${commit.id}, branchId=${branchAccess.branch.id}",
            )
        }
        return CommitAccess(
            authenticatedUserId = authenticatedUserId,
            workspace = branchAccess.workspace,
            branch = branchAccess.branch,
            commit = commit,
        )
    }
}
