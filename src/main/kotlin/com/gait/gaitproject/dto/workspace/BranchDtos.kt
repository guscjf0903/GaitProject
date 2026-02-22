package com.gait.gaitproject.dto.workspace

import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Workspace
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.UUID

data class BranchCreateRequest(
    @field:Schema(description = "워크스페이스 ID(경로값으로 주입됨)", nullable = true)
    @field:NotNull(message = "workspaceId는 필수입니다.")
    val workspaceId: UUID?,

    @field:Schema(description = "브랜치 이름", example = "main")
    @field:NotBlank(message = "name은 필수입니다.")
    val name: String,
    @field:Schema(description = "설명", example = "기본 브랜치", nullable = true)
    val description: String? = null,
    @field:Schema(description = "기본 브랜치 여부", example = "false")
    val isDefault: Boolean = false,
    @field:Schema(
        description = "브랜치 시작 기준 커밋 ID(선택). 지정하면 해당 커밋을 base/head로 설정",
        nullable = true
    )
    val baseCommitId: UUID? = null
) {
    fun toEntity(workspace: Workspace): Branch =
        Branch(
            workspace = workspace,
            name = name,
            description = description,
            isDefault = isDefault
        )
}

data class BranchResponse(
    val id: UUID?,
    val workspaceId: UUID?,
    val name: String,
    val description: String?,
    val headCommitId: UUID?,
    val baseCommitId: UUID?,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val createdAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime?,
    val deletedAt: OffsetDateTime?
) {
    companion object {
        fun fromEntity(entity: Branch): BranchResponse =
            BranchResponse(
                id = entity.id,
                workspaceId = entity.workspace.id,
                name = entity.name,
                description = entity.description,
                headCommitId = entity.headCommit?.id,
                baseCommitId = entity.baseCommit?.id,
                isDefault = entity.isDefault,
                isArchived = entity.isArchived,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt
            )
    }
}


