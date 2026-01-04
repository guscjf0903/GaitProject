package com.gait.gaitproject.dto.workspace

import com.gait.gaitproject.domain.user.entity.User
import com.gait.gaitproject.domain.workspace.entity.Workspace
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.UUID

data class WorkspaceCreateRequest(
    @field:Schema(description = "워크스페이스 소유자 유저 ID", example = "0b9b0a88-4c8b-4d7a-8e5f-8a0d5d2f1a11")
    @field:NotNull(message = "userId는 필수입니다.")
    val userId: UUID?,

    @field:Schema(description = "워크스페이스 이름", example = "My Workspace")
    @field:NotBlank(message = "name은 필수입니다.")
    val name: String,
    @field:Schema(description = "설명", example = "프로젝트 A 실험용", nullable = true)
    val description: String? = null
) {
    fun toEntity(user: User): Workspace =
        Workspace(
            user = user,
            name = name,
            description = description
        )
}

data class WorkspaceResponse(
    val id: UUID?,
    val userId: UUID?,
    val name: String,
    val description: String?,
    val isArchived: Boolean,
    val createdAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime?,
    val deletedAt: OffsetDateTime?
) {
    companion object {
        fun fromEntity(entity: Workspace): WorkspaceResponse =
            WorkspaceResponse(
                id = entity.id,
                userId = entity.user.id,
                name = entity.name,
                description = entity.description,
                isArchived = entity.isArchived,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt
            )
    }
}


