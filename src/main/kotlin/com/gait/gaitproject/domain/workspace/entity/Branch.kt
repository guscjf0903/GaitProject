package com.gait.gaitproject.domain.workspace.entity

import com.gait.gaitproject.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(
    name = "branches",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_branches_workspace_name", columnNames = ["workspace_id", "name"])
    ]
)
class Branch(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    var workspace: Workspace,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_commit_id")
    var headCommit: Commit? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_commit_id")
    var baseCommit: Commit? = null,

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,

    @Column(name = "is_archived", nullable = false)
    var isArchived: Boolean = false,

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null
) : BaseTimeEntity() {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null
        protected set

    @OneToMany(mappedBy = "branch", fetch = FetchType.LAZY)
    val commits: MutableList<Commit> = mutableListOf()
}


