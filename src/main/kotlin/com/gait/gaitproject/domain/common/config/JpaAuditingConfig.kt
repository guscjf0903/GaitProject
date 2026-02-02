package com.gait.gaitproject.domain.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.OffsetDateTime
import java.util.Optional

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
class JpaAuditingConfig {

    /**
     * Spring Data JPA Auditing은 기본적으로 LocalDateTime을 쓰는 경우가 있어,
     * OffsetDateTime 필드(createdAt/updatedAt)와 타입이 맞지 않으면 저장 시 예외가 발생합니다.
     *
     * - created_at/updated_at: TIMESTAMPTZ
     * - entity: OffsetDateTime
     */
    @Bean
    fun auditingDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of(OffsetDateTime.now()) }
}


