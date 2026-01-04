package com.gait.gaitproject.domain.billing.repository

import com.gait.gaitproject.domain.billing.entity.PaymentTransaction
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentTransactionRepository : JpaRepository<PaymentTransaction, UUID>


