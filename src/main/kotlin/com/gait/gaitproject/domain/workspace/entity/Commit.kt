package com.gait.gaitproject.domain.workspace.entity

import com.gait.gaitproject.domain.common.entity.BaseTimeEntity
import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(
    name = "commits",
    indexes = [
        Index(name = "idx_commits_branch_created", columnList = "branch_id, created_at")
    ]
)
class Commit(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    var workspace: Workspace,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    var branch: Branch,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Commit? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merge_parent_id")
    var mergeParent: Commit? = null,

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "merge_type", nullable = false, columnDefinition = "merge_type")
    var mergeType: MergeType = MergeType.NONE,

    @Column(name = "is_merge", nullable = false)
    var isMerge: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    var createdByUser: User? = null,

    @Column(name = "key_point", nullable = false, length = 255)
    var keyPoint: String,

    @Column(name = "short_summary", columnDefinition = "text")
    var shortSummary: String? = null,

    @Column(name = "long_summary", columnDefinition = "text")
    var longSummary: String? = null,

    /**
     * pgvector: VECTOR(1536)
     * NOTE: 실제 저장/조회는 pgvector용 Hibernate 타입을 붙여야 합니다.
     * 지금은 스키마 매핑(컬럼 정의)만 맞춰둡니다.
     */
    @Column(name = "embedding", columnDefinition = "vector(1536)")
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


