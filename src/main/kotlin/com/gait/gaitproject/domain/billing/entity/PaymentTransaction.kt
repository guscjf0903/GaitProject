package com.gait.gaitproject.domain.billing.entity

import com.gait.gaitproject.domain.common.entity.BaseTimeEntity
import com.gait.gaitproject.domain.common.enums.PaymentStatus
import com.gait.gaitproject.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(
    name = "payment_transactions",
    indexes = [
        Index(name = "idx_payment_transactions_user_created", columnList = "user_id, created_at")
    ]
)
class PaymentTransaction(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    var subscription: UserSubscription? = null,

    @Column(name = "amount_cents", nullable = false)
    var amountCents: Long,

    @Column(name = "currency", nullable = false, length = 10)
    var currency: String = "KRW",

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "payment_status")
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(name = "payment_provider", length = 50)
    var paymentProvider: String? = null,

    @Column(name = "external_payment_id", length = 255)
    var externalPaymentId: String? = null,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null
) : BaseTimeEntity() {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null
        protected set
}


