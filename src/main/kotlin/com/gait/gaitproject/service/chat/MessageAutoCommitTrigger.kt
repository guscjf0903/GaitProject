package com.gait.gaitproject.service.chat

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.service.workspace.CommitService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MessageAutoCommitTrigger(
    private val messageRepository: MessageRepository,
    private val commitService: CommitService,
) {
    companion object {
        private const val AUTO_COMMIT_THRESHOLD = 20L
    }

    fun triggerIfNeeded(workspaceId: UUID, branchId: UUID, authenticatedUserId: UUID) {
        val pendingCount = messageRepository.countByBranch_IdAndCommitIsNullAndDeletedAtIsNull(branchId)
        if (pendingCount < AUTO_COMMIT_THRESHOLD) return

        try {
            commitService.createAutoCommit(workspaceId, branchId, authenticatedUserId)
        } catch (_: IllegalArgumentException) {
            // 동시 요청으로 이미 커밋된 경우 등은 조용히 무시합니다.
        }
    }
}
