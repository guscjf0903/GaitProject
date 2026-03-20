package com.gait.gaitproject.service.rag

import com.gait.gaitproject.domain.common.enums.CreditEventType
import com.gait.gaitproject.domain.common.enums.PlanType
import com.gait.gaitproject.domain.credit.entity.CreditLog
import com.gait.gaitproject.domain.credit.repository.CreditLogRepository
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class RagQuotaResult(
    val allowed: Boolean,
    val reason: String,
    val remainingCalls: Int?
)

@Service
@Transactional(readOnly = true)
class RagQuotaService(
    private val userRepository: UserRepository,
    private val branchRepository: BranchRepository,
    private val creditLogRepository: CreditLogRepository
) {
    fun check(userId: UUID, planType: PlanType): RagQuotaResult {
        if (planType == PlanType.FREE) {
            return RagQuotaResult(false, "FREE plan: RAG disabled", null)
        }
        if (planType == PlanType.MASTER) {
            return RagQuotaResult(true, "MASTER plan: unlimited RAG", null)
        }

        val user = userRepository.findById(userId).orElseThrow {
            NotFoundException("User not found. id=$userId")
        }

        if (user.ragDailyLimit > 0 && user.ragCallsToday >= user.ragDailyLimit) {
            return RagQuotaResult(false, "Daily RAG limit reached", 0)
        }

        val remaining = if (user.ragDailyLimit > 0) user.ragDailyLimit - user.ragCallsToday else null
        return RagQuotaResult(true, "OK", remaining)
    }

    @Transactional
    fun deduct(userId: UUID, branchId: UUID): Int {
        val user = userRepository.findById(userId).orElseThrow {
            NotFoundException("User not found. id=$userId")
        }
        val branch = branchRepository.findById(branchId).orElseThrow {
            NotFoundException("Branch not found. id=$branchId")
        }

        user.ragCallsToday += 1
        userRepository.save(user)

        creditLogRepository.save(
            CreditLog(
                user = user,
                workspace = branch.workspace,
                branch = branch,
                eventType = CreditEventType.RAG_AUTO,
                description = "RAG_SEARCH",
                ragCreditsDelta = -1
            )
        )

        return if (user.ragDailyLimit > 0) user.ragDailyLimit - user.ragCallsToday else -1
    }
}
