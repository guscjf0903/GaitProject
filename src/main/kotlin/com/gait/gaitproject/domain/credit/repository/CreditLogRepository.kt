package com.gait.gaitproject.domain.credit.repository

import com.gait.gaitproject.domain.credit.entity.CreditLog
import org.springframework.data.jpa.repository.JpaRepository

interface CreditLogRepository : JpaRepository<CreditLog, Long>


