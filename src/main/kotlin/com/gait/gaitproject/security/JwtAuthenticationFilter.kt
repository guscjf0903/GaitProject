package com.gait.gaitproject.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val tokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)

        // Debug aid for local testing (do not log full tokens).
        // Helps identify cases where the browser thinks it sent Authorization but server didn't receive it.
        if (request.requestURI.startsWith("/api/")) {
            val summary = when {
                header.isNullOrBlank() -> "absent"
                header.startsWith("Bearer ") -> "bearer(len=${header.length})"
                else -> "present(prefix=${header.take(12)})"
            }
            // Use string interpolation to match commons-logging signature.
            logger.debug("AuthHeader ${request.method} ${request.requestURI} -> $summary")
        }

        if (!header.isNullOrBlank() && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ").trim()
            if (token.isBlank()) {
                filterChain.doFilter(request, response)
                return
            }

            try {
                val userId = tokenProvider.parseUserId(token)
                val principal = UserPrincipal(userId)
                val authentication = UsernamePasswordAuthenticationToken(principal, token, emptyList())
                SecurityContextHolder.getContext().authentication = authentication
            } catch (e: Exception) {
                // 토큰이 있는데 유효하지 않으면 401로 종료.
                // sendError()는 /error로 디스패치되어 보안 설정에 따라 403으로 덮일 수 있으므로,
                // 여기서는 status를 직접 내려서 클라이언트가 401을 정확히 보도록 합니다.
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = "text/plain;charset=UTF-8"
                response.writer.use { it.write("Invalid token") }
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}


