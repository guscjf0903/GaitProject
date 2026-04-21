package com.gait.gaitproject.controller.workspace

import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.dto.workspace.WorkspaceCreateRequest
import com.gait.gaitproject.dto.workspace.WorkspaceResponse
import com.gait.gaitproject.security.UserPrincipal
import com.gait.gaitproject.service.workspace.WorkspaceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Workspaces", description = "Workspace 생성/조회")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/workspaces")
class WorkspaceController(
    private val workspaceService: WorkspaceService
) {
    @Operation(summary = "워크스페이스 생성", description = "userId, name 필수. 생성 시 기본 브랜치도 함께 생성됩니다(서비스 구현에 따름).")
    @PostMapping
    fun create(
        @Valid @RequestBody request: WorkspaceCreateRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<ApiResponse<WorkspaceResponse>> =
        ResponseEntity.ok(ApiResponse.ok(workspaceService.create(request, principal.userId)))

    @Operation(summary = "워크스페이스 조회", description = "workspaceId로 상세 조회합니다.")
    @GetMapping("/{workspaceId}")
    fun get(
        @PathVariable workspaceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<ApiResponse<WorkspaceResponse>> =
        ResponseEntity.ok(ApiResponse.ok(workspaceService.getOwnedResponse(workspaceId, principal.userId)))
}
