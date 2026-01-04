package com.gait.gaitproject.controller.user

import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.dto.workspace.WorkspaceResponse
import com.gait.gaitproject.service.workspace.WorkspaceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Users", description = "유저 리소스")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users/{userId}/workspaces")
class UserWorkspaceController(
    private val workspaceService: WorkspaceService
) {
    @Operation(summary = "유저의 워크스페이스 목록", description = "userId로 소유한 workspace 목록을 조회합니다.")
    @GetMapping
    fun list(@PathVariable userId: UUID): ResponseEntity<ApiResponse<List<WorkspaceResponse>>> =
        ResponseEntity.ok(ApiResponse.ok(workspaceService.listByUser(userId)))
}





