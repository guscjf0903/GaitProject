package com.gait.gaitproject.domain.workspace.repository

import com.gait.gaitproject.domain.workspace.entity.Commit
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommitRepository : JpaRepository<Commit, UUID> {

    fun findByBranch_IdAndDeletedAtIsNullOrderByCreatedAtDesc(branchId: UUID, pageable: Pageable): List<Commit>
}


