package com.gait.gaitproject.domain.credit.entity

import com.gait.gaitproject.domain.common.entity.CreatedAtEntity
import com.gait.gaitproject.domain.common.enums.CreditEventType
import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Workspace
import com.gait.gaitproject.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "credit_logs",
    indexes = [
        Index(name = "idx_credit_logs_user_created", columnList = "user_id, created_at"),
        Index(name = "idx_credit_logs_type_created", columnList = "event_type, created_at")
    ]
)
class CreditLog(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    var workspace: Workspace? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    var branch: Branch? = null,

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "event_type", nullable = false, columnDefinition = "credit_event_type")
    var eventType: CreditEventType,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,

    @Column(name = "model_name", length = 100)
    var modelName: String? = null,

    @Column(name = "input_tokens")
    var inputTokens: Int? = null,

    @Column(name = "output_tokens")
    var outputTokens: Int? = null,

    @Column(name = "amount_cents")
    var amountCents: Long? = null,

    @Column(name = "rag_credits_delta")
    var ragCreditsDelta: Int? = null
) : CreatedAtEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    var id: Long? = null
        protected set
}


