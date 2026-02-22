package com.gait.gaitproject.dto.chat

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class ChatStreamRequest(
    @field:Schema(description = "워크스페이스 ID", example = "0b9b0a88-4c8b-4d7a-8e5f-8a0d5d2f1a11")
    @field:NotNull(message = "workspaceId는 필수입니다.")
    val workspaceId: UUID?,

    @field:Schema(description = "브랜치 ID", example = "3c4c7e63-2c2e-4b23-9b8c-1c0d0e3a7f91")
    @field:NotNull(message = "branchId는 필수입니다.")
    val branchId: UUID?,

    @field:Schema(
        description = "컨텍스트 기준 커밋 ID(체크아웃/타임트래블 시점). null이면 브랜치 HEAD 기준",
        example = "8c1c7a4b-5ec4-4d58-9eb7-f1f5f6d533c0",
        nullable = true
    )
    val contextCommitId: UUID? = null,

    @field:Schema(description = "사용자 질문/메시지 내용", example = "이 브랜치의 최근 대화를 요약해줘")
    @field:NotBlank(message = "content는 필수입니다.")
    val content: String
)


