package com.gait.gaitproject.domain.workspace.repository

import com.gait.gaitproject.domain.workspace.entity.Workspace
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkspaceRepository : JpaRepository<Workspace, UUID> {

    fun findByUser_IdAndNameAndDeletedAtIsNull(userId: UUID, name: String): Workspace?

    fun findByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId: UUID): List<Workspace>
}


