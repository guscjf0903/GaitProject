package com.gait.gaitproject.exception

import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.service.common.ForbiddenException
import com.gait.gaitproject.service.common.NotFoundException
import com.gait.gaitproject.service.common.UnauthorizedException
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.slf4j.LoggerFactory

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException) =
        org.springframework.http.ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(code = "NOT_FOUND", message = ex.message ?: "Not Found"))

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException) =
        org.springframework.http.ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(code = "UNAUTHORIZED", message = ex.message ?: "Unauthorized"))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException) =
        org.springframework.http.ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(code = "FORBIDDEN", message = ex.message ?: "Forbidden"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException) =
        org.springframework.http.ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(code = "BAD_REQUEST", message = ex.message ?: "Bad Request"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException) =
        org.springframework.http.ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ApiResponse.error(
                    code = "VALIDATION_ERROR",
                    message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Validation Error",
                    data = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
                )
            )

    @ExceptionHandler(BindException::class)
    fun handleBindException(ex: BindException) =
        org.springframework.http.ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ApiResponse.error(
                    code = "VALIDATION_ERROR",
                    message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Validation Error",
                    data = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
                )
            )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException) =
        org.springframework.http.ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ApiResponse.error(
                    code = "VALIDATION_ERROR",
                    message = ex.constraintViolations.firstOrNull()?.message ?: "Validation Error",
                    data = ex.constraintViolations.associate { it.propertyPath.toString() to it.message }
                )
            )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(ex: HttpMessageNotReadableException) =
        org.springframework.http.ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(code = "BAD_REQUEST", message = "요청 본문(JSON) 파싱에 실패했습니다."))

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        // 서버 내부 로그에는 상세를 남기고, 응답은 일반 메시지로 유지
        log.error("Unhandled exception", ex)
        return org.springframework.http.ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(code = "INTERNAL_ERROR", message = "서버 오류가 발생했습니다."))
    }
}




