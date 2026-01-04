package com.gait.gaitproject.service.chat

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.WorkspaceRepository
import com.gait.gaitproject.dto.chat.MessageResponse
import com.gait.gaitproject.dto.chat.MessageSendRequest
import com.gait.gaitproject.dto.workspace.CommitCreateRequest
import com.gait.gaitproject.service.common.NotFoundException
import com.gait.gaitproject.service.workspace.CommitService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MessageService(
    private val messageRepository: MessageRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val branchRepository: BranchRepository,
    private val userRepository: UserRepository,
    private val commitService: CommitService
) {
    fun timelineAfter(branchId: UUID, afterSequenceExclusive: Long, limit: Int): List<MessageResponse> =
        messageRepository.findByBranch_IdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequenceAsc(
            branchId = branchId,
            afterSequenceExclusive = afterSequenceExclusive,
            pageable = PageRequest.of(0, limit)
        ).map(MessageResponse::fromEntity)

    @Transactional
    fun send(request: MessageSendRequest): MessageResponse {
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

        val user = request.userId?.let { userId ->
            userRepository.findById(userId).orElseThrow { NotFoundException("User not found. id=$userId") }
        }

        val last = messageRepository.findTopByBranch_IdAndDeletedAtIsNullOrderBySequenceDesc(branch.id!!)
        val nextSequence = (last?.sequence ?: 0L) + 1L

        val saved = messageRepository.save(request.toEntity(workspace, branch, user, nextSequence))

        // MVP: 자동 커밋 트리거(예: 미커밋 메시지 20개 이상)
        val pendingCount = messageRepository.countByBranch_IdAndCommitIsNullAndDeletedAtIsNull(branch.id!!)
        if (pendingCount >= 20) {
            commitService.create(
                CommitCreateRequest(
                    workspaceId = workspace.id,
                    branchId = branch.id,
                    keyPoint = "AUTO_COMMIT",
                    shortSummary = "Auto commit by threshold",
                    longSummary = "Auto commit triggered when pending messages >= 20"
                ),
                createdByUserId = user?.id
            )
        }

        return MessageResponse.fromEntity(saved)
    }
}


