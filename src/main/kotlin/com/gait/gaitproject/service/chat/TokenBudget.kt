package com.gait.gaitproject.service.chat

import com.gait.gaitproject.domain.common.enums.PlanType

/**
 * 플랜별 토큰 예산 할당 (설계서 2.1 기준)
 * 
 * 영역별 비율:
 * - System & Query: ~10%
 * - Recent History: ~50%
 * - Head Context: ~25%
 * - Lineage History: ~15%
 */
data class TokenBudget(
    val totalInput: Int,
    val totalOutput: Int,
    val systemQuery: Int,      // ~10%
    val recentHistory: Int,    // ~50%
    val headContext: Int,      // ~25%
    val lineageHistory: Int    // ~15%
) {
    companion object {
        /**
         * 플랜별 토큰 예산 생성 (설계서 1장 기준)
         */
        fun fromPlan(plan: PlanType): TokenBudget {
            return when (plan) {
                PlanType.FREE -> TokenBudget(
                    totalInput = 4000,
                    totalOutput = 800,
                    systemQuery = 400,      // 10%
                    recentHistory = 2000,   // 50%
                    headContext = 1000,     // 25%
                    lineageHistory = 600    // 15%
                )
                PlanType.STANDARD -> TokenBudget(
                    totalInput = 8000,
                    totalOutput = 1500,
                    systemQuery = 800,      // 10%
                    recentHistory = 4000,   // 50%
                    headContext = 2000,     // 25%
                    lineageHistory = 1200   // 15%
                )
                PlanType.MASTER -> TokenBudget(
                    totalInput = 16000,
                    totalOutput = 2500,
                    systemQuery = 1600,     // 10%
                    recentHistory = 8000,   // 50%
                    headContext = 4000,     // 25%
                    lineageHistory = 2400   // 15%
                )
            }
        }
    }
    
    /**
     * 남은 예산 계산 (Lineage Backfill에서 사용)
     */
    fun remainingAfter(used: Int): Int = totalInput - used
}


