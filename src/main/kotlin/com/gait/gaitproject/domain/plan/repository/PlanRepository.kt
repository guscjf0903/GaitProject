package com.gait.gaitproject.domain.plan.repository

import com.gait.gaitproject.domain.plan.entity.Plan
import org.springframework.data.jpa.repository.JpaRepository

interface PlanRepository : JpaRepository<Plan, Int>


