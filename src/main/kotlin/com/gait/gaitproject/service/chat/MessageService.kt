package com.gait.gaitproject.service.chat

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.dto.chat.MessageResponse
import com.gait.gaitproject.dto.chat.MessageSendRequest
import com.gait.gaitproject.service.access.AccessGuard
import com.gait.gaitproject.service.common.ForbiddenException
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MessageService(
    private val accessGuard: AccessGuard,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val messageAutoCommitTrigger: MessageAutoCommitTrigger,
) {
    fun timelineAfter(
        workspaceId: UUID,
        branchId: UUID,
        authenticatedUserId: UUID,
        afterSequenceExclusive: Long,
        limit: Int,
    ): List<MessageResponse> {
        accessGuard.requireBranchAccess(workspaceId, branchId, authenticatedUserId)
        return messageRepository.findByBranch_IdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequenceAsc(
            branchId = branchId,
            afterSequenceExclusive = afterSequenceExclusive,
            pageable = PageRequest.of(0, limit)
        ).map(MessageResponse::fromEntity)
    }

    fun timelineUpToCommit(
        workspaceId: UUID,
        branchId: UUID,
        commitId: UUID,
        authenticatedUserId: UUID,
        limit: Int,
    ): List<MessageResponse> {
        val commitAccess = accessGuard.requireCommitAccess(workspaceId, branchId, commitId, authenticatedUserId)

        val ids = ArrayList<UUID>(64)
        var current: Commit? = commitAccess.commit
        while (current != null) {
            ids.add(requireNotNull(current.id))
            current = current.parent
        }

        if (ids.isEmpty()) {
            return emptyList()
        }

        val messages = messageRepository
            .findByWorkspace_IdAndCommit_IdInAndDeletedAtIsNull(commitAccess.workspace.id!!, ids)
            .sortedWith(compareBy({ it.createdAt }, { it.sequence }))

        val capped = if (messages.size > limit) messages.takeLast(limit) else messages
        return capped.map(MessageResponse::fromEntity)
    }

    @Transactional
    fun send(request: MessageSendRequest, authenticatedUserId: UUID): MessageResponse {
        val workspaceId = requireNotNull(request.workspaceId)
        val branchId = requireNotNull(request.branchId)
        val branchAccess = accessGuard.requireBranchAccess(workspaceId, branchId, authenticatedUserId)

        if (request.userId != null && request.userId != authenticatedUserId) {
            throw ForbiddenException("다른 사용자 ID로 메시지를 저장할 수 없습니다.")
        }

        val user = userRepository.findById(authenticatedUserId).orElseThrow {
            NotFoundException("User not found. id=$authenticatedUserId")
        }

        val last = messageRepository.findTopByBranch_IdAndDeletedAtIsNullOrderBySequenceDesc(branchAccess.branch.id!!)
        val nextSequence = (last?.sequence ?: 0L) + 1L

        val saved = messageRepository.save(
            request.toEntity(
                workspace = branchAccess.workspace,
                branch = branchAccess.branch,
                user = user,
                sequence = nextSequence,
            )
        )

        messageAutoCommitTrigger.triggerIfNeeded(
            workspaceId = branchAccess.workspace.id!!,
            branchId = branchAccess.branch.id!!,
            authenticatedUserId = authenticatedUserId,
        )

        return MessageResponse.fromEntity(saved)
    }
}
