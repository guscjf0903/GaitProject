package com.gait.gaitproject.dto.user

import com.gait.gaitproject.domain.common.enums.PlanType
import com.gait.gaitproject.domain.user.entity.User

data class UserProfileResponse(
    val email: String,
    val name: String?,
    val plan: PlanType,
    val ragCallsToday: Int,
    val ragDailyLimit: Int,
    val ragRemaining: Int?,
    val cheapTokensUsed: Long,
    val premiumWalletCents: Long
) {
    companion object {
        fun fromEntity(user: User): UserProfileResponse {
            val remaining = if (user.ragDailyLimit > 0)
                (user.ragDailyLimit - user.ragCallsToday).coerceAtLeast(0) else null
            return UserProfileResponse(
                email = user.email,
                name = user.name,
                plan = user.plan,
                ragCallsToday = user.ragCallsToday,
                ragDailyLimit = user.ragDailyLimit,
                ragRemaining = remaining,
                cheapTokensUsed = user.cheapTokensUsed,
                premiumWalletCents = user.premiumWalletCents
            )
        }
    }
}
