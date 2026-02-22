package com.gait.gaitproject.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtTokenProvider: JwtTokenProvider
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .cors { } // WebMvcConfigurer 기반 CORS 설정을 사용
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight
                    .requestMatchers("/api/auth/**").permitAll()
                    // Spring Boot error endpoint (token invalid 등 sendError 시 /error로 포워딩될 수 있음)
                    .requestMatchers("/error").permitAll()
                    // /api/chat/stream은 인증 필요 (JWT principal 사용)
                    // 테스트를 위해 permitAll()로 두려면 주석 해제
                    // .requestMatchers(HttpMethod.POST, "/api/chat/stream").permitAll()
                    // Swagger UI / OpenAPI (SpringDoc)
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter::class.java
            )
            .build()
    }
}


