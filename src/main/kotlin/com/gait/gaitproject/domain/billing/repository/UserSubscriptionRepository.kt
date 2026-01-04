package com.gait.gaitproject.domain.billing.repository

import com.gait.gaitproject.domain.billing.entity.UserSubscription
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserSubscriptionRepository : JpaRepository<UserSubscription, UUID>


