package com.gait.gaitproject.dto.chat

import com.gait.gaitproject.domain.chat.entity.Message
import com.gait.gaitproject.domain.common.enums.MessageRole
import com.gait.gaitproject.domain.user.entity.User
import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Workspace
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.UUID

data class MessageSendRequest(
    @field:Schema(description = "워크스페이스 ID(경로값으로 주입됨)", nullable = true)
    @field:NotNull(message = "workspaceId는 필수입니다.")
    val workspaceId: UUID?,

    @field:Schema(description = "브랜치 ID(경로값으로 주입됨)", nullable = true)
    @field:NotNull(message = "branchId는 필수입니다.")
    val branchId: UUID?,
    @field:Schema(description = "작성자 유저 ID(옵션)", nullable = true)
    val userId: UUID? = null,
    @field:Schema(description = "메시지 역할", example = "USER")
    @field:NotNull(message = "role은 필수입니다.")
    val role: MessageRole?,

    @field:Schema(description = "메시지 본문", example = "안녕! 워크스페이스 만들어줘")
    @field:NotBlank(message = "content는 필수입니다.")
    val content: String,
    @field:Schema(description = "메타데이터(JSON 문자열 등)", nullable = true)
    val metadata: String? = null,
    
    // AI 토큰 및 페이로드 관련 필드 추가
    val rawPrompt: String? = null,
    val rawResponse: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val modelName: String? = null
) {
    fun toEntity(workspace: Workspace, branch: Branch, user: User?, sequence: Long): Message =
        Message(
            workspace = workspace,
            branch = branch,
            user = user,
            role = requireNotNull(role),
            content = content,
            metadata = metadata,
            sequence = sequence,
            rawPrompt = rawPrompt,
            rawResponse = rawResponse,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            modelName = modelName
        )
}

data class MessageResponse(
    val id: UUID?,
    val workspaceId: UUID?,
    val branchId: UUID?,
    val commitId: UUID?,
    val userId: UUID?,
    val role: MessageRole,
    val content: String,
    val metadata: String?,
    val sequence: Long,
    val rawPrompt: String?,
    val rawResponse: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val totalTokens: Int?,
    val modelName: String?,
    val createdAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime?,
    val deletedAt: OffsetDateTime?
) {
    companion object {
        fun fromEntity(entity: Message): MessageResponse =
            MessageResponse(
                id = entity.id,
                workspaceId = entity.workspace.id,
                branchId = entity.branch.id,
                commitId = entity.commit?.id,
                userId = entity.user?.id,
                role = entity.role,
                content = entity.content,
                metadata = entity.metadata,
                sequence = entity.sequence,
                rawPrompt = entity.rawPrompt,
                rawResponse = entity.rawResponse,
                inputTokens = entity.inputTokens,
                outputTokens = entity.outputTokens,
                totalTokens = entity.totalTokens,
                modelName = entity.modelName,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt
            )
    }
}


