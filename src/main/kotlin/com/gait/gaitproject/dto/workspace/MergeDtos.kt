package com.gait.gaitproject.dto.workspace

import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.user.entity.User
import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.entity.Merge
import com.gait.gaitproject.domain.workspace.entity.Workspace
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.UUID

data class MergeCreateRequest(
    @field:Schema(description = "워크스페이스 ID(경로값으로 주입됨)", nullable = true)
    @field:NotNull(message = "workspaceId는 필수입니다.")
    val workspaceId: UUID?,

    @field:Schema(description = "머지 출발 브랜치 ID", example = "3c4c7e63-2c2e-4b23-9b8c-1c0d0e3a7f91")
    @field:NotNull(message = "fromBranchId는 필수입니다.")
    val fromBranchId: UUID?,

    @field:Schema(description = "머지 대상 브랜치 ID", example = "f0a1b2c3-d4e5-6789-aaaa-bbbbccccdddd")
    @field:NotNull(message = "toBranchId는 필수입니다.")
    val toBranchId: UUID?,

    @field:Schema(description = "머지 타입", example = "SQUASH")
    @field:NotNull(message = "mergeType은 필수입니다.")
    val mergeType: MergeType?,
    @field:Schema(description = "머지 노트(옵션)", example = "A 브랜치 변경사항을 main에 통합", nullable = true)
    val notes: String? = null
) {
    fun toEntity(
        workspace: Workspace,
        fromBranch: Branch,
        toBranch: Branch,
        fromCommit: Commit?,
        toCommit: Commit?,
        mergeCommit: Commit?,
        initiatedByUser: User?
    ): Merge =
        Merge(
            workspace = workspace,
            fromBranch = fromBranch,
            toBranch = toBranch,
            fromCommit = fromCommit,
            toCommit = toCommit,
            mergeCommit = mergeCommit,
            mergeType = requireNotNull(mergeType),
            initiatedByUser = initiatedByUser,
            notes = notes
        )
}

data class MergeResponse(
    val id: UUID?,
    val workspaceId: UUID?,
    val fromBranchId: UUID?,
    val toBranchId: UUID?,
    val fromCommitId: UUID?,
    val toCommitId: UUID?,
    val mergeCommitId: UUID?,
    val mergeType: MergeType,
    val initiatedByUserId: UUID?,
    val notes: String?,
    val createdAt: OffsetDateTime?
) {
    companion object {
        fun fromEntity(entity: Merge): MergeResponse =
            MergeResponse(
                id = entity.id,
                workspaceId = entity.workspace.id,
                fromBranchId = entity.fromBranch.id,
                toBranchId = entity.toBranch.id,
                fromCommitId = entity.fromCommit?.id,
                toCommitId = entity.toCommit?.id,
                mergeCommitId = entity.mergeCommit?.id,
                mergeType = entity.mergeType,
                initiatedByUserId = entity.initiatedByUser?.id,
                notes = entity.notes,
                createdAt = entity.createdAt
            )
    }
}


