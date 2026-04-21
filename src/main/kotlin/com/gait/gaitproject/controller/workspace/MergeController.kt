package com.gait.gaitproject.controller.workspace

import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.dto.workspace.MergeCreateRequest
import com.gait.gaitproject.dto.workspace.MergeResponse
import com.gait.gaitproject.security.UserPrincipal
import com.gait.gaitproject.service.workspace.MergeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Merges", description = "브랜치 머지")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/merges")
class MergeController(
    private val mergeService: MergeService
) {
    @Operation(summary = "머지 생성", description = "fromBranchId → toBranchId로 merge를 수행하고 merge 기록을 생성합니다.")
    @PostMapping
    fun create(
        @PathVariable workspaceId: UUID,
        @Valid @RequestBody request: MergeCreateRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ApiResponse<MergeResponse>> =
        ResponseEntity.ok(ApiResponse.ok(mergeService.create(request.copy(workspaceId = workspaceId), principal.userId)))
}

