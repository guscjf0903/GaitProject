package com.gait.gaitproject.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import java.util.UUID

@Component
class JwtTokenProvider(
    @Value("\${app.jwt.secret}") secret: String,
    @Value("\${app.jwt.ttl-seconds:86400}") private val ttlSeconds: Long
) {
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    fun createAccessToken(userId: UUID): String {
        val now = Instant.now()
        val exp = now.plusSeconds(ttlSeconds)
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key)
            .compact()
    }

    fun parseUserId(token: String): UUID {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        return UUID.fromString(claims.subject)
    }
}


