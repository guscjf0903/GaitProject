package com.gait.gaitproject.domain.user.entity

import com.gait.gaitproject.domain.common.entity.BaseTimeEntity
import com.gait.gaitproject.domain.common.enums.PlanType
import com.gait.gaitproject.domain.workspace.entity.Workspace
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Id
import jakarta.persistence.GeneratedValue
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "users")
class User(

    @Column(name = "email", nullable = false, unique = true, length = 255)
    var email: String,

    @Column(name = "name", length = 100)
    var name: String? = null,

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "plan", nullable = false, columnDefinition = "plan_type")
    var plan: PlanType = PlanType.FREE,

    @Column(name = "premium_wallet_cents", nullable = false)
    var premiumWalletCents: Long = 0,

    @Column(name = "cheap_tokens_used", nullable = false)
    var cheapTokensUsed: Long = 0,

    @Column(name = "rag_calls_today", nullable = false)
    var ragCallsToday: Int = 0,

    @Column(name = "rag_daily_limit", nullable = false)
    var ragDailyLimit: Int = 0,

    @Column(name = "timezone", length = 50)
    var timezone: String? = "Asia/Seoul",

    @Column(name = "locale", length = 10)
    var locale: String? = "ko-KR",

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null
) : BaseTimeEntity() {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null
        protected set

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    val workspaces: MutableList<Workspace> = mutableListOf()
}


