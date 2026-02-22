package com.gait.gaitproject.domain.chat.repository

import com.gait.gaitproject.domain.chat.entity.Message
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MessageRepository : JpaRepository<Message, UUID> {

    fun findByBranch_IdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequenceAsc(
        branchId: UUID,
        afterSequenceExclusive: Long,
        pageable: Pageable
    ): List<Message>

    fun findTopByBranch_IdAndDeletedAtIsNullOrderBySequenceDesc(branchId: UUID): Message?

    fun findByBranch_IdAndCommitIsNullAndDeletedAtIsNullOrderBySequenceAsc(branchId: UUID): List<Message>

    fun findByBranch_IdAndCommitIsNullAndDeletedAtIsNullOrderBySequenceDesc(branchId: UUID, pageable: Pageable): List<Message>

    fun countByBranch_IdAndCommitIsNullAndDeletedAtIsNull(branchId: UUID): Long

    fun findByBranch_IdAndCommit_IdInAndDeletedAtIsNullOrderBySequenceAsc(
        branchId: UUID,
        commitIds: List<UUID>
    ): List<Message>

    fun findByWorkspace_IdAndCommit_IdInAndDeletedAtIsNull(
        workspaceId: UUID,
        commitIds: List<UUID>
    ): List<Message>
}


