package com.gait.gaitproject.domain.billing.repository

import com.gait.gaitproject.domain.billing.entity.UserMonthlyUsage
import org.springframework.data.jpa.repository.JpaRepository

interface UserMonthlyUsageRepository : JpaRepository<UserMonthlyUsage, Long>


