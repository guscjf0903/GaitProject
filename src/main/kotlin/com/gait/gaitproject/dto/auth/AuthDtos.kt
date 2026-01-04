package com.gait.gaitproject.dto.auth

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class SignupRequest(
    @field:Schema(description = "이메일(유저 식별자)", example = "user@example.com")
    @field:Email(message = "email 형식이 올바르지 않습니다.")
    @field:NotBlank(message = "email은 필수입니다.")
    val email: String,

    @field:Schema(description = "표시 이름", example = "홍길동", nullable = true)
    val name: String? = null
)

data class LoginRequest(
    @field:Schema(description = "이메일(유저 식별자)", example = "user@example.com")
    @field:Email(message = "email 형식이 올바르지 않습니다.")
    @field:NotBlank(message = "email은 필수입니다.")
    val email: String
)

data class TokenResponse(
    @field:Schema(description = "토큰 타입", example = "Bearer")
    val tokenType: String = "Bearer",
    @field:Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    val accessToken: String,
    @field:Schema(description = "발급된 토큰의 유저 ID", example = "0b9b0a88-4c8b-4d7a-8e5f-8a0d5d2f1a11")
    val userId: UUID
)


