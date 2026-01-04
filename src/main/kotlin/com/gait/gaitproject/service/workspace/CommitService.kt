package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.CommitRepository
import com.gait.gaitproject.domain.workspace.repository.WorkspaceRepository
import com.gait.gaitproject.dto.workspace.CommitCreateRequest
import com.gait.gaitproject.dto.workspace.CommitCreateResultResponse
import com.gait.gaitproject.dto.workspace.CommitResponse
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CommitService(
    private val commitRepository: CommitRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val branchRepository: BranchRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun create(request: CommitCreateRequest, createdByUserId: UUID? = null): CommitCreateResultResponse {
        val workspaceId = requireNotNull(request.workspaceId)
        val branchId = requireNotNull(request.branchId)

        val workspace = workspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Workspace not found. id=$workspaceId")
        }
        val branch = branchRepository.findById(branchId).orElseThrow {
            NotFoundException("Branch not found. id=$branchId")
        }
        if (branch.workspace.id != workspace.id) {
            throw IllegalArgumentException("Branch does not belong to workspace. branchId=${branch.id}, workspaceId=${workspace.id}")
        }

        val createdByUser = createdByUserId?.let { userId ->
            userRepository.findById(userId).orElseThrow { NotFoundException("User not found. id=$userId") }
        }

        val parent = branch.headCommit
        val commit = commitRepository.save(request.toEntity(workspace, branch, parent, createdByUser))

        // branch head 갱신
        branch.headCommit = commit
        branchRepository.save(branch)

        // 아직 commit_id 없는 메시지들을 이 커밋에 귀속
        val pendingMessages = messageRepository.findByBranch_IdAndCommitIsNullAndDeletedAtIsNullOrderBySequenceAsc(branch.id!!)
        pendingMessages.forEach { it.commit = commit }
        messageRepository.saveAll(pendingMessages)

        return CommitCreateResultResponse(
            commit = CommitResponse.fromEntity(commit),
            attachedMessageCount = pendingMessages.size
        )
    }
}


