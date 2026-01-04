package com.gait.gaitproject.domain.billing.entity

import com.gait.gaitproject.domain.common.entity.BaseTimeEntity
import com.gait.gaitproject.domain.common.enums.PlanType
import com.gait.gaitproject.domain.common.enums.SubscriptionStatus
import com.gait.gaitproject.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(
    name = "user_subscriptions",
    indexes = [
        Index(name = "idx_user_subscriptions_user_status", columnList = "user_id, status")
    ]
)
class UserSubscription(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "plan", nullable = false, columnDefinition = "plan_type")
    var plan: PlanType,

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "subscription_status")
    var status: SubscriptionStatus = SubscriptionStatus.ACTIVE,

    @Column(name = "starts_at", nullable = false)
    var startsAt: OffsetDateTime,

    @Column(name = "ends_at", nullable = false)
    var endsAt: OffsetDateTime,

    @Column(name = "payment_provider", length = 50)
    var paymentProvider: String? = null,

    @Column(name = "external_sub_id", length = 255)
    var externalSubId: String? = null,

    @Column(name = "memo", columnDefinition = "text")
    var memo: String? = null
) : BaseTimeEntity() {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null
        protected set
}


