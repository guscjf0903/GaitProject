package com.gait.gaitproject.controller.chat

import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.dto.chat.MessageResponse
import com.gait.gaitproject.dto.chat.MessageSendRequest
import com.gait.gaitproject.service.chat.MessageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Messages", description = "메시지 전송/조회")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/branches/{branchId}/messages")
class MessageController(
    private val messageService: MessageService
) {
    @Operation(summary = "메시지 전송", description = "브랜치 내 메시지를 저장합니다. role(USER/ASSISTANT 등), content가 필요합니다.")
    @PostMapping
    fun send(
        @PathVariable workspaceId: UUID,
        @PathVariable branchId: UUID,
        @Valid @RequestBody request: MessageSendRequest
    ): ResponseEntity<ApiResponse<MessageResponse>> =
        ResponseEntity.ok(ApiResponse.ok(messageService.send(request.copy(workspaceId = workspaceId, branchId = branchId))))

    @Operation(summary = "브랜치 타임라인 조회(after)", description = "branchId 기준 sequence 오름차순. after(배타), limit 기본 50")
    @GetMapping("/timeline")
    fun timelineAfter(
        @PathVariable branchId: UUID,
        @RequestParam(name = "after", defaultValue = "0") afterSequenceExclusive: Long,
        @RequestParam(name = "limit", defaultValue = "50") limit: Int
    ): ResponseEntity<ApiResponse<List<MessageResponse>>> =
        ResponseEntity.ok(ApiResponse.ok(messageService.timelineAfter(branchId, afterSequenceExclusive, limit)))
}


