package com.gait.gaitproject.controller.user

import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.dto.user.UserProfileResponse
import com.gait.gaitproject.security.UserPrincipal
import com.gait.gaitproject.service.user.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User Profile", description = "현재 사용자 프로필 및 잔여량 조회")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users/me")
class UserProfileController(
    private val userService: UserService
) {
    @Operation(summary = "내 프로필 조회", description = "JWT로 인증된 사용자의 플랜, RAG 잔여 호출 수, 토큰 사용량을 반환합니다.")
    @GetMapping
    fun me(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<ApiResponse<UserProfileResponse>> {
        val user = userService.get(principal.userId)
        return ResponseEntity.ok(ApiResponse.ok(UserProfileResponse.fromEntity(user)))
    }
}
