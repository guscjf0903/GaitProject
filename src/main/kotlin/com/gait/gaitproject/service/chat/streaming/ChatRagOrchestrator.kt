package com.gait.gaitproject.service.chat.streaming

import com.gait.gaitproject.domain.common.enums.PlanType
import com.gait.gaitproject.service.rag.RagQuotaService
import com.gait.gaitproject.service.rag.RagRouteType
import com.gait.gaitproject.service.rag.RagRouter
import com.gait.gaitproject.service.rag.RagService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

data class ChatRagContext(
    val ragSection: String?,
)

@Service
class ChatRagOrchestrator(
    private val ragRouter: RagRouter,
    private val ragService: RagService,
    private val ragQuotaService: RagQuotaService,
    @Value("\${app.ai.rag.token-budget:2000}") private val ragTokenBudget: Int,
    @Value("\${app.ai.rag.max-results:5}") private val ragMaxResults: Int,
) {
    fun resolve(
        query: String,
        branchId: UUID,
        planType: PlanType,
        authenticatedUserId: UUID,
        sseWriter: ChatSseWriter,
    ): ChatRagContext {
        val routeDecision = ragRouter.route(query, planType)
        if (routeDecision.type != RagRouteType.EXTRACTION_SEARCH) {
            return ChatRagContext(ragSection = null)
        }

        val quota = ragQuotaService.check(authenticatedUserId, planType)
        if (!quota.allowed) {
            return ChatRagContext(ragSection = null)
        }

        val ragResult = ragService.search(
            query = query,
            branchId = branchId,
            tokenBudget = ragTokenBudget,
            limit = ragMaxResults,
        )

        val ragSection = if (ragResult.itemCount > 0) {
            ragQuotaService.deduct(authenticatedUserId, branchId)
            ragResult.formattedContext
        } else {
            null
        }

        sseWriter.sendChatEvent(
            eventName = "RAG_STATUS",
            data = mapOf(
                "searched" to true,
                "itemCount" to ragResult.itemCount,
                "searchDurationMs" to ragResult.searchDurationMs,
                "routeType" to routeDecision.type.name,
                "remainingCalls" to quota.remainingCalls,
            ),
        )

        return ChatRagContext(ragSection = ragSection)
    }
}
