package com.gait.gaitproject.service.rag

import com.gait.gaitproject.domain.common.enums.PlanType
import org.springframework.stereotype.Component

enum class RagRouteType {
    DIRECT,
    FAST_RETRIEVAL,
    EXTRACTION_SEARCH
}

data class RagRouteDecision(
    val type: RagRouteType,
    val reason: String
)

@Component
class RagRouter {

    private val searchKeywords = listOf(
        "이전에", "아까", "지난번에", "지난", "찾아", "기억", "예전에", "검색", "저번에",
        "했었", "했던", "말했", "얘기했", "논의했", "정했던", "결정했던",
        "remember", "search", "find", "earlier", "before", "previous"
    )

    fun route(query: String, planType: PlanType): RagRouteDecision {
        if (planType == PlanType.FREE) {
            return RagRouteDecision(RagRouteType.DIRECT, "FREE plan: RAG disabled")
        }

        val lower = query.lowercase()
        val matchCount = searchKeywords.count { lower.contains(it) }

        if (matchCount == 0) {
            return RagRouteDecision(RagRouteType.DIRECT, "No retrieval keywords")
        }

        if (matchCount == 1 && query.length < 20) {
            return RagRouteDecision(RagRouteType.FAST_RETRIEVAL, "Weak retrieval signal")
        }

        return RagRouteDecision(
            RagRouteType.EXTRACTION_SEARCH,
            "Retrieval signal detected (keywords=$matchCount)"
        )
    }
}
