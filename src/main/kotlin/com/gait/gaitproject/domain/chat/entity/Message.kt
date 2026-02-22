package com.gait.gaitproject.domain.chat.entity

import com.gait.gaitproject.domain.common.entity.BaseTimeEntity
import com.gait.gaitproject.domain.common.enums.MessageRole
import com.gait.gaitproject.domain.workspace.entity.Branch
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.entity.Workspace
import com.gait.gaitproject.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(
    name = "messages",
    indexes = [
        Index(name = "idx_messages_branch_sequence", columnList = "branch_id, sequence"),
        Index(name = "idx_messages_branch_created", columnList = "branch_id, created_at")
    ]
)
class Message(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    var workspace: Workspace,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    var branch: Branch,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_id")
    var commit: Commit? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "message_role")
    var role: MessageRole,

    @Column(name = "content", nullable = false, columnDefinition = "text")
    var content: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    var metadata: String? = null,

    @Column(name = "sequence", nullable = false)
    var sequence: Long,

    /**
     * pgvector: VECTOR(1536)
     * NOTE: 실제 저장/조회는 pgvector용 Hibernate 타입을 붙여야 합니다.
     */
    // 현재는 Hibernate 타입 매핑이 없어서 insert 시 타입 미스매치가 날 수 있음.
    // (vector 컬럼에 varchar 파라미터를 바인딩하려고 시도)
    // 임베딩을 실제로 쓰기 전까지는 DB 기본값(NULL)에 맡기고, 추후 pgvector 타입을 붙입니다.
    @Column(name = "embedding", columnDefinition = "vector(1536)", insertable = false, updatable = false)
    var embedding: String? = null,

    @Column(name = "input_tokens")
    var inputTokens: Int? = null,

    @Column(name = "output_tokens")
    var outputTokens: Int? = null,

    @Column(name = "model_name", length = 100)
    var modelName: String? = null,

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null
) : BaseTimeEntity() {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null
        protected set
}


