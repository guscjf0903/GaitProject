package com.gait.gaitproject.domain.workspace.repository

import com.gait.gaitproject.domain.workspace.entity.Branch
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BranchRepository : JpaRepository<Branch, UUID> {

    fun findByWorkspace_IdAndDeletedAtIsNullOrderByCreatedAtAsc(workspaceId: UUID): List<Branch>

    fun findByWorkspace_IdAndIsDefaultTrueAndDeletedAtIsNull(workspaceId: UUID): Branch?
}


