package com.gait.gaitproject.controller.workspace

import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.dto.workspace.CommitCreateRequest
import com.gait.gaitproject.dto.workspace.CommitCreateResultResponse
import com.gait.gaitproject.service.workspace.CommitService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Commits", description = "커밋 생성")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/branches/{branchId}/commits")
class CommitController(
    private val commitService: CommitService
) {
    @Operation(summary = "커밋 생성", description = "브랜치의 최근 메시지를 커밋에 부착하고, 커밋 요약 정보를 저장합니다(서비스 구현에 따름).")
    @PostMapping
    fun create(
        @PathVariable workspaceId: UUID,
        @PathVariable branchId: UUID,
        @Valid @RequestBody request: CommitCreateRequest,
        @RequestParam(required = false) createdByUserId: UUID?
    ): ResponseEntity<ApiResponse<CommitCreateResultResponse>> =
        ResponseEntity.ok(
            ApiResponse.ok(
                commitService.create(
                    request.copy(workspaceId = workspaceId, branchId = branchId),
                    createdByUserId
                )
            )
        )
}


