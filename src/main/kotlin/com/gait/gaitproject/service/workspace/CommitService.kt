package com.gait.gaitproject.service.workspace

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.CommitRepository
import com.gait.gaitproject.domain.workspace.repository.WorkspaceRepository
import com.gait.gaitproject.dto.workspace.CommitCreateRequest
import com.gait.gaitproject.dto.workspace.CommitCreateResultResponse
import com.gait.gaitproject.dto.workspace.CommitResponse
import com.gait.gaitproject.service.ai.CommitSummaryAiService
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
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
    private val userRepository: UserRepository,
    @Autowired(required = false) private val commitSummaryAiService: CommitSummaryAiService?
) {
    fun list(workspaceId: UUID, branchId: UUID, limit: Int): List<CommitResponse> {
        val workspace = workspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Workspace not found. id=$workspaceId")
        }
        val branch = branchRepository.findById(branchId).orElseThrow {
            NotFoundException("Branch not found. id=$branchId")
        }
        if (branch.workspace.id != workspace.id) {
            throw IllegalArgumentException("Branch does not belong to workspace. branchId=${branch.id}, workspaceId=${workspace.id}")
        }

        val commits = commitRepository.findByBranch_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
            branchId = branch.id!!,
            pageable = PageRequest.of(0, limit.coerceIn(1, 500))
        )
        return commits.map(CommitResponse::fromEntity)
    }

    @Transactional
    fun create(request: CommitCreateRequest, createdByUserId: UUID? = null): CommitCreateResultResponse {
        val workspaceId = requireNotNull(request.workspaceId)
        val branchId = requireNotNull(request.branchId)

        val workspace = workspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Workspace not found. id=$workspaceId")
        }
        // 🔒 브랜치 커밋 생성 구간 직렬화(중복 커밋 방지)
        val branch = branchRepository.findByIdForUpdate(branchId) ?: throw NotFoundException("Branch not found. id=$branchId")
        // (락 걸린 branch를 기준으로 workspace 소속 검증)
        if (branch.workspace.id != workspace.id) {
            throw IllegalArgumentException("Branch does not belong to workspace. branchId=${branch.id}, workspaceId=${workspace.id}")
        }
        /*
         * NOTE:
         * findByIdForUpdate로 가져온 branch는 이미 검증된 상태이므로
         * 기존 findById + 검증 로직을 대체합니다.
         */
        /*
        val branch = branchRepository.findById(branchId).orElseThrow {
            NotFoundException("Branch not found. id=$branchId")
        }
        if (branch.workspace.id != workspace.id) {
            throw IllegalArgumentException("Branch does not belong to workspace. branchId=${branch.id}, workspaceId=${workspace.id}")
        }
        */

        val createdByUser = createdByUserId?.let { userId ->
            userRepository.findById(userId).orElseThrow { NotFoundException("User not found. id=$userId") }
        }

        val parent = branch.headCommit

        // 커밋 대상 메시지(미커밋)들을 먼저 확보하여 요약 생성에 사용할 수 있게 합니다.
        val pendingMessages = messageRepository.findByBranch_IdAndCommitIsNullAndDeletedAtIsNullOrderBySequenceAsc(branch.id!!)
        if (pendingMessages.isEmpty()) {
            throw IllegalArgumentException("No pending messages to commit.")
        }

        val needsSummary = request.shortSummary.isNullOrBlank() || request.longSummary.isNullOrBlank() || request.keyPoint == "AUTO_COMMIT"
        val aiSummary = if (needsSummary) {
            commitSummaryAiService?.summarize(pendingMessages, parent?.longSummary)
        } else {
            null
        }

        val finalKeyPoint = when {
            request.keyPoint == "AUTO_COMMIT" && !aiSummary?.keyPoint.isNullOrBlank() -> aiSummary!!.keyPoint!!.trim()
            request.keyPoint.isNotBlank() -> request.keyPoint.trim()
            else -> "COMMIT"
        }
        val finalShortSummary = request.shortSummary?.takeIf { it.isNotBlank() } ?: aiSummary?.shortSummary
        val finalLongSummary = request.longSummary?.takeIf { it.isNotBlank() } ?: aiSummary?.longSummary

        val commit = commitRepository.save(
            request.copy(
                keyPoint = finalKeyPoint,
                shortSummary = finalShortSummary,
                longSummary = finalLongSummary
            ).toEntity(workspace, branch, parent, createdByUser)
        )

        // branch head 갱신
        branch.headCommit = commit
        branchRepository.save(branch)

        // 아직 commit_id 없는 메시지들을 이 커밋에 귀속
        pendingMessages.forEach { it.commit = commit }
        messageRepository.saveAll(pendingMessages)

        return CommitCreateResultResponse(
            commit = CommitResponse.fromEntity(commit),
            attachedMessageCount = pendingMessages.size
        )
    }
}


