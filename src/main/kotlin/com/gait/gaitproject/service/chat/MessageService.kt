package com.gait.gaitproject.service.chat

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.CommitRepository
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
    private val commitRepository: CommitRepository,
    private val userRepository: UserRepository,
    private val commitService: CommitService
) {
    fun timelineAfter(branchId: UUID, afterSequenceExclusive: Long, limit: Int): List<MessageResponse> =
        messageRepository.findByBranch_IdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequenceAsc(
            branchId = branchId,
            afterSequenceExclusive = afterSequenceExclusive,
            pageable = PageRequest.of(0, limit)
        ).map(MessageResponse::fromEntity)

    fun timelineUpToCommit(workspaceId: UUID, branchId: UUID, commitId: UUID, limit: Int): List<MessageResponse> {
        val workspace = workspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Workspace not found. id=$workspaceId")
        }
        val branch = branchRepository.findById(branchId).orElseThrow {
            NotFoundException("Branch not found. id=$branchId")
        }
        if (branch.workspace.id != workspace.id) {
            throw IllegalArgumentException("Branch does not belong to workspace. branchId=${branch.id}, workspaceId=${workspace.id}")
        }

        val commit = commitRepository.findById(commitId).orElseThrow {
            NotFoundException("Commit not found. id=$commitId")
        }
        if (commit.branch.id != branch.id) {
            throw IllegalArgumentException("Commit does not belong to branch. commitId=${commit.id}, branchId=${branch.id}")
        }

        // target commit 포함, root까지 조상 커밋들을 모아 해당 시점까지의 메시지만 로드
        val ids = ArrayList<UUID>(64)
        var cur: com.gait.gaitproject.domain.workspace.entity.Commit? = commit
        while (cur != null) {
            ids.add(requireNotNull(cur.id))
            cur = cur.parent
        }

        // JPA IN 절은 빈 리스트를 처리하지 못할 수 있으므로 early return
        if (ids.isEmpty()) {
            return emptyList()
        }

        val messages = messageRepository
            .findByWorkspace_IdAndCommit_IdInAndDeletedAtIsNull(workspace.id!!, ids)
            .sortedWith(compareBy({ it.createdAt }, { it.sequence }))

        val capped = if (messages.size > limit) messages.takeLast(limit) else messages
        return capped.map(MessageResponse::fromEntity)
    }

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
            try {
                commitService.create(
                    CommitCreateRequest(
                        workspaceId = workspace.id,
                        branchId = branch.id,
                        keyPoint = "AUTO_COMMIT"
                    ),
                    createdByUserId = user?.id
                )
            } catch (_: IllegalArgumentException) {
                // 동시 요청으로 이미 커밋이 처리된 경우(락 대기 후 pending=0 등) 무시
            }
        }

        return MessageResponse.fromEntity(saved)
    }
}


