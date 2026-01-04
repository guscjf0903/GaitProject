package com.gait.gaitproject.dto.chat

import io.swagger.v3.oas.annotations.media.Schema

data class SseEvent<T>(
    @field:Schema(description = "이벤트 타입", example = "ANSWER_CHUNK")
    val type: String,
    @field:Schema(description = "이벤트 데이터(타입에 따라 구조가 달라질 수 있음)", nullable = true)
    val data: T? = null,
    @field:Schema(description = "메시지(옵션)", example = "과거 기록 검색 중... (-1 Credit)", nullable = true)
    val msg: String? = null
)


