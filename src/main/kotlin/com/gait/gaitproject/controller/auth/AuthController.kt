package com.gait.gaitproject.controller.auth

import com.gait.gaitproject.dto.auth.LoginRequest
import com.gait.gaitproject.dto.auth.SignupRequest
import com.gait.gaitproject.dto.auth.TokenResponse
import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.security.JwtTokenProvider
import com.gait.gaitproject.service.user.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as OasApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "회원가입/로그인 (JWT 발급)")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService,
    private val jwtTokenProvider: JwtTokenProvider
) {
    @Operation(summary = "회원가입(또는 미존재 시 생성) 후 토큰 발급", description = "MVP 단계: password 없이 email 기반으로 유저를 생성/조회하고 JWT를 발급합니다.")
    @ApiResponses(
        value = [
            OasApiResponse(
                responseCode = "200",
                description = "성공",
                content = [Content(schema = Schema(implementation = com.gait.gaitproject.dto.common.ApiResponse::class))]
            )
        ]
    )
    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<ApiResponse<TokenResponse>> {
        val user = userService.createIfNotExists(request.email, request.name)
        val token = jwtTokenProvider.createAccessToken(user.id!!)
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse(accessToken = token, userId = user.id!!)))
    }

    @Operation(summary = "로그인(이메일 기반) 후 토큰 발급", description = "MVP 단계: email로 유저를 조회하고 JWT를 발급합니다.")
    @ApiResponses(
        value = [
            OasApiResponse(
                responseCode = "200",
                description = "성공",
                content = [Content(schema = Schema(implementation = com.gait.gaitproject.dto.common.ApiResponse::class))]
            )
        ]
    )
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ApiResponse<TokenResponse>> {
        val user = userService.getByEmail(request.email)
        val token = jwtTokenProvider.createAccessToken(user.id!!)
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse(accessToken = token, userId = user.id!!)))
    }
}


