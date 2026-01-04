package com.gait.gaitproject.dto.common

data class ApiResponse<T>(
    val code: String,
    val message: String,
    val data: T? = null
) {
    companion object {
        fun <T> ok(data: T, message: String = "OK"): ApiResponse<T> =
            ApiResponse(code = "OK", message = message, data = data)

        fun ok(message: String = "OK"): ApiResponse<Unit> =
            ApiResponse(code = "OK", message = message, data = Unit)

        fun error(code: String, message: String, data: Any? = null): ApiResponse<Any> =
            ApiResponse(code = code, message = message, data = data)
    }
}





