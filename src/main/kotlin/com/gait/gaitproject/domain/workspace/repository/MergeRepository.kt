package com.gait.gaitproject.domain.workspace.repository

import com.gait.gaitproject.domain.workspace.entity.Merge
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MergeRepository : JpaRepository<Merge, UUID>


