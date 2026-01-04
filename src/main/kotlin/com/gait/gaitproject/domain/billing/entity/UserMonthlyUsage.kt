package com.gait.gaitproject.domain.billing.entity

import com.gait.gaitproject.domain.common.entity.BaseTimeEntity
import com.gait.gaitproject.domain.user.entity.User
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "user_monthly_usage",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_user_monthly_usage_user_month", columnNames = ["user_id", "year_month"])
    ],
    indexes = [
        Index(name = "idx_user_monthly_usage_user_month", columnList = "user_id, year_month")
    ]
)
class UserMonthlyUsage(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(name = "year_month", nullable = false)
    var yearMonth: LocalDate,

    @Column(name = "premium_tokens_used", nullable = false)
    var premiumTokensUsed: Long = 0,

    @Column(name = "cheap_tokens_used", nullable = false)
    var cheapTokensUsed: Long = 0,

    @Column(name = "rag_calls", nullable = false)
    var ragCalls: Long = 0
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    var id: Long? = null
        protected set
}


