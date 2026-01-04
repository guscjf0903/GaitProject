package com.gait.gaitproject.domain.workspace.entity

import com.gait.gaitproject.domain.common.entity.CreatedAtEntity
import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(
    name = "merges",
    indexes = [
        Index(name = "idx_merges_workspace_created", columnList = "workspace_id, created_at")
    ]
)
class Merge(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    var workspace: Workspace,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_branch_id", nullable = false)
    var fromBranch: Branch,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_branch_id", nullable = false)
    var toBranch: Branch,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_commit_id")
    var fromCommit: Commit? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_commit_id")
    var toCommit: Commit? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merge_commit_id")
    var mergeCommit: Commit? = null,

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "merge_type", nullable = false, columnDefinition = "merge_type")
    var mergeType: MergeType,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by_user_id")
    var initiatedByUser: User? = null,

    @Column(name = "notes", columnDefinition = "text")
    var notes: String? = null
) : CreatedAtEntity() {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null
        protected set
}


