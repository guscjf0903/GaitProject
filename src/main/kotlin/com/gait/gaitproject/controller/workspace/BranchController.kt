package com.gait.gaitproject.controller.workspace

import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.dto.workspace.BranchCreateRequest
import com.gait.gaitproject.dto.workspace.BranchResponse
import com.gait.gaitproject.security.UserPrincipal
import com.gait.gaitproject.service.workspace.BranchService
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

@Tag(name = "Branches", description = "브랜치 생성/목록")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/branches")
class BranchController(
    private val branchService: BranchService
) {
    @Operation(summary = "브랜치 생성", description = "workspaceId 경로값 + body(name, description, isDefault)")
    @PostMapping
    fun create(
        @PathVariable workspaceId: UUID,
        @Valid @RequestBody request: BranchCreateRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<ApiResponse<BranchResponse>> =
        ResponseEntity.ok(ApiResponse.ok(branchService.create(request.copy(workspaceId = workspaceId), principal.userId)))

    @Operation(summary = "워크스페이스의 브랜치 목록", description = "workspaceId로 브랜치 목록을 조회합니다.")
    @GetMapping
    fun listByWorkspace(
        @PathVariable workspaceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<ApiResponse<List<BranchResponse>>> =
        ResponseEntity.ok(ApiResponse.ok(branchService.listByWorkspace(workspaceId, principal.userId)))
}

