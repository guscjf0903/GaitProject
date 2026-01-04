package com.gait.gaitproject.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val tokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)

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
                // 토큰이 있는데 유효하지 않으면 401로 종료(모호한 상태로 흘려보내지 않음)
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token")
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}


