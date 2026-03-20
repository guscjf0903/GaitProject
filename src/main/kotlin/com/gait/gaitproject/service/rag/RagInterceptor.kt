package com.gait.gaitproject.service.rag

import com.gait.gaitproject.domain.common.enums.CreditEventType
import com.gait.gaitproject.domain.credit.entity.CreditLog
import com.gait.gaitproject.domain.credit.repository.CreditLogRepository
import com.gait.gaitproject.domain.user.repository.UserRepository
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class RagDecision(
    val used: Boolean,
    val reason: String?,
    val injectedText: String?
)

@Deprecated("Replaced by RagRouter + RagService + RagQuotaService")
@Transactional(readOnly = true)
class RagInterceptor(
    private val userRepository: UserRepository,
    private val branchRepository: BranchRepository,
    private val creditLogRepository: CreditLogRepository
) {
    /**
     * MVP: \"RAG 필요\" 판단을 단순 휴리스틱으로 시작합니다.
     * - 실제 구현은 Tool calling + pgvector 검색으로 교체 예정
     */
    @Transactional
    fun maybeUseRag(userId: UUID, branchId: UUID, query: String): RagDecision {
        val lower = query.lowercase()
        val needs = listOf("아까", "이전", "지난", "찾아", "remember").any { lower.contains(it) }
        if (!needs) return RagDecision(false, null, null)

        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found. id=$userId") }
        val branch = branchRepository.findById(branchId).orElseThrow { NotFoundException("Branch not found. id=$branchId") }

        // 일일 한도 체크(0이면 무제한 취급)
        if (user.ragDailyLimit > 0 && user.ragCallsToday >= user.ragDailyLimit) {
            return RagDecision(true, "RAG_DAILY_LIMIT_REACHED", "[RAG] Daily limit reached. Continue without retrieval.")
        }

        user.ragCallsToday += 1
        userRepository.save(user)

        creditLogRepository.save(
            CreditLog(
                user = user,
                workspace = branch.workspace,
                branch = branch,
                eventType = CreditEventType.RAG_AUTO,
                description = "AUTO_RAG"
            )
        )

        // MVP: 실제 검색 대신 \"검색했다\"는 힌트만 주입(다음 단계에서 pgvector 결과로 대체)
        return RagDecision(true, "RAG_USED", "[RAG] Retrieved related history (stub).")
    }
}


