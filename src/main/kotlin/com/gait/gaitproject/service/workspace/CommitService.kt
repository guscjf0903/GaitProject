package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.CommitRepository
import com.gait.gaitproject.dto.workspace.CommitCreateRequest
import com.gait.gaitproject.dto.workspace.CommitCreateResultResponse
import com.gait.gaitproject.dto.workspace.CommitResponse
import com.gait.gaitproject.service.access.AccessGuard
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.gait.gaitproject.service.workspace.commit.CommitEmbeddingSaver
import com.gait.gaitproject.service.workspace.commit.CommitSummaryResolver
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CommitService(
    private val commitRepository: CommitRepository,
    private val branchRepository: BranchRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val accessGuard: AccessGuard,
    private val commitSummaryResolver: CommitSummaryResolver,
    private val commitEmbeddingSaver: CommitEmbeddingSaver,
) {
    fun list(workspaceId: UUID, branchId: UUID, authenticatedUserId: UUID, limit: Int): List<CommitResponse> {
        val branchAccess = accessGuard.requireBranchAccess(workspaceId, branchId, authenticatedUserId)

        val commits = commitRepository.findByBranch_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
            branchId = branchAccess.branch.id!!,
            pageable = PageRequest.of(0, limit.coerceIn(1, 500))
        )
        return commits.map(CommitResponse::fromEntity)
    }

    @Transactional
    fun create(request: CommitCreateRequest, authenticatedUserId: UUID): CommitCreateResultResponse {
        val workspaceId = requireNotNull(request.workspaceId)
        val branchId = requireNotNull(request.branchId)
        val branchAccess = accessGuard.requireBranchAccess(workspaceId, branchId, authenticatedUserId)

        // 🔒 브랜치 커밋 생성 구간 직렬화(중복 커밋 방지)
        val branch = branchRepository.findByIdForUpdate(branchId) ?: throw NotFoundException("Branch not found. id=$branchId")
        if (branch.workspace.id != branchAccess.workspace.id) {
            throw IllegalArgumentException(
                "Branch does not belong to workspace. branchId=${branch.id}, workspaceId=${branchAccess.workspace.id}",
            )
        }

        val createdByUser = userRepository.findById(authenticatedUserId).orElseThrow {
            NotFoundException("User not found. id=$authenticatedUserId")
        }

        val parent = branch.headCommit

        // 커밋 대상 메시지(미커밋)들을 먼저 확보하여 요약 생성에 사용할 수 있게 합니다.
        val pendingMessages = messageRepository.findByBranch_IdAndCommitIsNullAndDeletedAtIsNullOrderBySequenceAsc(branch.id!!)
        if (pendingMessages.isEmpty()) {
            throw IllegalArgumentException("No pending messages to commit.")
        }

        val resolvedSummary = commitSummaryResolver.resolve(
            request = request,
            pendingMessages = pendingMessages,
            parentLongSummary = parent?.longSummary,
        )

        val commit = commitRepository.save(
            request.copy(
                keyPoint = resolvedSummary.keyPoint,
                shortSummary = resolvedSummary.shortSummary,
                longSummary = resolvedSummary.longSummary
            ).toEntity(branchAccess.workspace, branch, parent, createdByUser)
        )

        // branch head 갱신
        branch.headCommit = commit
        branchRepository.save(branch)

        // 아직 commit_id 없는 메시지들을 이 커밋에 귀속
        pendingMessages.forEach { it.commit = commit }
        messageRepository.saveAll(pendingMessages)

        // JPA 변경사항을 DB에 반영 후 JDBC 기반 임베딩 저장
        commitRepository.flush()
        commitEmbeddingSaver.save(commit)

        return CommitCreateResultResponse(
            commit = CommitResponse.fromEntity(commit),
            attachedMessageCount = pendingMessages.size
        )
    }

    @Transactional
    fun createAutoCommit(workspaceId: UUID, branchId: UUID, authenticatedUserId: UUID): CommitCreateResultResponse {
        return create(
            request = CommitCreateRequest(
                workspaceId = workspaceId,
                branchId = branchId,
                keyPoint = "AUTO_COMMIT",
            ),
            authenticatedUserId = authenticatedUserId,
        )
    }
}

