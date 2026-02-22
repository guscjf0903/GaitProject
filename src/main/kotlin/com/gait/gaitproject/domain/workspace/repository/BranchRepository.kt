package com.gait.gaitproject.domain.workspace.repository

import com.gait.gaitproject.domain.workspace.entity.Branch
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface BranchRepository : JpaRepository<Branch, UUID> {

    fun findByWorkspace_IdAndDeletedAtIsNullOrderByCreatedAtAsc(workspaceId: UUID): List<Branch>

    fun findByWorkspace_IdAndIsDefaultTrueAndDeletedAtIsNull(workspaceId: UUID): Branch?

    fun findByWorkspace_IdAndNameAndDeletedAtIsNull(workspaceId: UUID, name: String): Branch?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): Branch?
}


