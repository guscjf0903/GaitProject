package com.gait.gaitproject.dto.workspace

import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.user.entity.User
import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.entity.Workspace
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.UUID

data class CommitCreateRequest(
    @field:Schema(description = "워크스페이스 ID(경로값으로 주입됨)", nullable = true)
    @field:NotNull(message = "workspaceId는 필수입니다.")
    val workspaceId: UUID?,

    @field:Schema(description = "브랜치 ID(경로값으로 주입됨)", nullable = true)
    @field:NotNull(message = "branchId는 필수입니다.")
    val branchId: UUID?,

    @field:Schema(description = "커밋 제목/핵심", example = "로그인 JWT 버그 수정")
    @field:NotBlank(message = "keyPoint는 필수입니다.")
    val keyPoint: String,
    @field:Schema(description = "짧은 요약(선택)", example = "토큰 만료 시간을 1시간에서 24시간으로 변경", nullable = true)
    val shortSummary: String? = null,
    @field:Schema(description = "긴 요약(선택)", example = "사용자가 401 에러를 호소하여... (상세)", nullable = true)
    val longSummary: String? = null
) {
    fun toEntity(
        workspace: Workspace,
        branch: Branch,
        parent: Commit?,
        createdByUser: User?
    ): Commit =
        Commit(
            workspace = workspace,
            branch = branch,
            parent = parent,
            createdByUser = createdByUser,
            keyPoint = keyPoint,
            shortSummary = shortSummary,
            longSummary = longSummary,
            mergeType = MergeType.NONE,
            isMerge = false
        )
}

data class CommitResponse(
    val id: UUID?,
    val workspaceId: UUID?,
    val branchId: UUID?,
    val parentId: UUID?,
    val mergeParentId: UUID?,
    val mergeType: MergeType,
    val isMerge: Boolean,
    val createdByUserId: UUID?,
    val keyPoint: String,
    val shortSummary: String?,
    val longSummary: String?,
    val createdAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime?,
    val deletedAt: OffsetDateTime?
) {
    companion object {
        fun fromEntity(entity: Commit): CommitResponse =
            CommitResponse(
                id = entity.id,
                workspaceId = entity.workspace.id,
                branchId = entity.branch.id,
                parentId = entity.parent?.id,
                mergeParentId = entity.mergeParent?.id,
                mergeType = entity.mergeType,
                isMerge = entity.isMerge,
                createdByUserId = entity.createdByUser?.id,
                keyPoint = entity.keyPoint,
                shortSummary = entity.shortSummary,
                longSummary = entity.longSummary,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt
            )
    }
}

data class CommitCreateResultResponse(
    @field:Schema(description = "생성된 커밋")
    val commit: CommitResponse,
    @field:Schema(description = "이 커밋에 부착된 메시지 개수", example = "20")
    val attachedMessageCount: Int
)


