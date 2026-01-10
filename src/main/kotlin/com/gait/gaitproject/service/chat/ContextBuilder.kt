package com.gait.gaitproject.service.chat

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.domain.common.enums.PlanType
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class BuiltContext(
    val systemPrompt: String,
    val recentMessages: List<String>,
    val headLongSummary: String?,
    val lineageSummaries: List<String>,
    val combined: String,
    val tokenUsage: TokenUsage
)

data class TokenUsage(
    val systemQuery: Int,
    val recentHistory: Int,
    val headContext: Int,
    val lineageHistory: Int,
    val total: Int
)

@Service
@Transactional(readOnly = true)
class ContextBuilder(
    private val branchRepository: BranchRepository,
    private val messageRepository: MessageRepository
) {
    /**
     * 설계서 2.1 기준: 토큰 예산 할당에 따라 컨텍스트 구성
     * - System & Query: ~10%
     * - Recent History: ~50%
     * - Head Context: ~25%
     * - Lineage History: ~15%
     */
    fun build(branchId: UUID, userQuery: String, planType: PlanType): BuiltContext {
        val branch = branchRepository.findById(branchId).orElseThrow {
            NotFoundException("Branch not found. id=$branchId")
        }

        val budget = TokenBudget.fromPlan(planType)

        // 1. System & Query (~10%)
        val systemPrompt = buildSystemPrompt()
        val systemQueryTokens = TokenEstimator.estimate(systemPrompt + userQuery)
        val systemQueryText = "$systemPrompt\n\n[USER_QUERY]\n$userQuery"

        // 2. Recent History (~50%)
        val recent = buildRecentMessages(branchId, budget.recentHistory)
        val recentTokens = TokenEstimator.estimateTotal(recent)

        // 3. Head Context (~25%)
        // 설계서: "25%를 모두 채우지 않았다면 이어져있는 조상 브랜치의 LongSummary를 가져와서 요약"
        val headContext = buildHeadContext(branch, budget.headContext, recentTokens + systemQueryTokens)
        val headTokens = TokenEstimator.estimate(headContext.text)

        // 4. Lineage History (~15%, Adaptive Lineage Backfill)
        val usedSoFar = systemQueryTokens + recentTokens + headTokens
        val remainingBudget = budget.remainingAfter(usedSoFar)
        val lineage = buildAdaptiveLineage(
            branch.headCommit?.parent,
            budget.lineageHistory,
            remainingBudget
        )
        val lineageTokens = TokenEstimator.estimateTotal(lineage)

        val combined = buildString {
            appendLine("[SYSTEM]")
            appendLine(systemPrompt)
            appendLine()
            appendLine("[HEAD_LONG_SUMMARY]")
            appendLine(headContext.text)
            appendLine()
            appendLine("[LINEAGE]")
            lineage.forEach { appendLine(it) }
            appendLine()
            appendLine("[RECENT]")
            recent.forEach { appendLine(it) }
            appendLine()
            appendLine("[USER_QUERY]")
            appendLine(userQuery)
        }

        return BuiltContext(
            systemPrompt = systemPrompt,
            recentMessages = recent,
            headLongSummary = headContext.text.takeIf { it.isNotBlank() },
            lineageSummaries = lineage,
            combined = combined,
            tokenUsage = TokenUsage(
                systemQuery = systemQueryTokens,
                recentHistory = recentTokens,
                headContext = headTokens,
                lineageHistory = lineageTokens,
                total = systemQueryTokens + recentTokens + headTokens + lineageTokens
            )
        )
    }

    private fun buildSystemPrompt(): String {
        return """You are a helpful AI assistant for a Git-based conversation management system.
Follow these rules:
- Provide clear, concise answers
- Reference previous context when relevant
- Use the conversation history to maintain continuity"""
    }

    private fun buildRecentMessages(branchId: UUID, budget: Int): List<String> {
        val messages = messageRepository
            .findByBranch_IdAndCommitIsNullAndDeletedAtIsNullOrderBySequenceDesc(branchId, PageRequest.of(0, 50))
            .asReversed()

        val result = mutableListOf<String>()
        var usedTokens = 0

        for (msg in messages) {
            val text = "${msg.role}: ${msg.content}"
            val tokens = TokenEstimator.estimate(text)
            if (usedTokens + tokens > budget) break
            result.add(text)
            usedTokens += tokens
        }

        return result
    }

    private data class HeadContextResult(val text: String)

    /**
     * Head Context (~25%)
     * 설계서: "25%를 모두 채우지 않았다면 이어져있는 조상 브랜치의 LongSummary를 가져와서 요약"
     */
    private fun buildHeadContext(
        branch: com.gait.gaitproject.domain.workspace.entity.Branch,
        budget: Int,
        usedSoFar: Int
    ): HeadContextResult {
        val headCommit = branch.headCommit ?: return HeadContextResult("")
        val headText = headCommit.longSummary ?: headCommit.shortSummary ?: headCommit.keyPoint ?: ""
        val headTokens = TokenEstimator.estimate(headText)

        // 예산이 남으면 조상 브랜치의 Long Summary도 포함
        val remaining = budget - headTokens
        if (remaining > 0 && headCommit.parent != null) {
            val ancestorTexts = mutableListOf<String>()
            var cur = headCommit.parent
            var ancestorTokens = 0

            while (cur != null && ancestorTokens < remaining) {
                val ancestorLong = cur.longSummary
                if (ancestorLong != null) {
                    val tokens = TokenEstimator.estimate(ancestorLong)
                    if (ancestorTokens + tokens <= remaining) {
                        ancestorTexts.add(ancestorLong)
                        ancestorTokens += tokens
                    } else {
                        break
                    }
                }
                cur = cur.parent
            }

            if (ancestorTexts.isNotEmpty()) {
                return HeadContextResult(
                    buildString {
                        appendLine(headText)
                        if (ancestorTexts.isNotEmpty()) {
                            appendLine()
                            appendLine("[ANCESTOR_CONTEXT]")
                            ancestorTexts.forEach { appendLine(it) }
                        }
                    }
                )
            }
        }

        return HeadContextResult(headText)
    }

    /**
     * Adaptive Lineage Backfill (설계서 2.2)
     * - 예산이 넉넉할 때 (70% 이상 남음) → Short Summary
     * - 예산이 30% 미만 남았을 때 → Key Point만
     */
    private fun buildAdaptiveLineage(
        startCommit: Commit?,
        budget: Int,
        remainingBudget: Int
    ): List<String> {
        val result = mutableListOf<String>()
        var cur = startCommit
        var usedTokens = 0
        val threshold = (budget * 0.3).toInt() // 30% 임계값

        while (cur != null && usedTokens < budget) {
            val remaining = budget - usedTokens
            val text = when {
                // 예산이 70% 이상 남음 → Short Summary
                remaining >= threshold -> {
                    cur.shortSummary ?: cur.keyPoint ?: ""
                }
                // 예산이 30% 미만 → Key Point만
                else -> {
                    cur.keyPoint ?: ""
                }
            }

            if (text.isBlank()) {
                cur = cur.parent
                continue
            }

            val formatted = "- ${cur.id}: $text"
            val tokens = TokenEstimator.estimate(formatted)

            if (usedTokens + tokens > budget) break

            result.add(formatted)
            usedTokens += tokens
            cur = cur.parent
        }

        return result
    }
}


