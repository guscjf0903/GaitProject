package com.gait.gaitproject.domain.plan.entity

import com.gait.gaitproject.domain.common.entity.BaseTimeEntity
import com.gait.gaitproject.domain.common.enums.PlanType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "plans")
class Plan(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    var id: Int? = null,

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "code", nullable = false, unique = true, columnDefinition = "plan_type")
    var code: PlanType,

    @Column(name = "name", nullable = false, length = 50)
    var name: String,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,

    @Column(name = "input_limit_tokens", nullable = false)
    var inputLimitTokens: Int,

    @Column(name = "output_limit_tokens", nullable = false)
    var outputLimitTokens: Int,

    @Column(name = "cheap_pool_monthly", nullable = false)
    var cheapPoolMonthly: Long,

    @Column(name = "premium_wallet_monthly", nullable = false)
    var premiumWalletMonthly: Long,

    @Column(name = "rag_daily_limit_default", nullable = false)
    var ragDailyLimitDefault: Int
) : BaseTimeEntity()


