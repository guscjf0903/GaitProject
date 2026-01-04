package com.gait.gaitproject.domain.workspace.entity

import com.gait.gaitproject.domain.common.entity.BaseTimeEntity
import com.gait.gaitproject.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(
    name = "workspaces",
    indexes = [
        Index(name = "idx_workspaces_user", columnList = "user_id")
    ]
)
class Workspace(

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,

    /**
     * FK는 스키마에서 "생성 후 업데이트"로 가정.
     * nullable 허용.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_branch_id")
    var defaultBranch: Branch? = null,

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

    @OneToMany(mappedBy = "workspace", fetch = FetchType.LAZY)
    val branches: MutableList<Branch> = mutableListOf()
}


