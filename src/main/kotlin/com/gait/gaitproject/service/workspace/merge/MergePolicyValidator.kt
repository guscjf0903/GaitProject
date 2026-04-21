package com.gait.gaitproject.service.workspace.merge

import com.gait.gaitproject.domain.common.enums.MergeType
import com.gait.gaitproject.domain.common.enums.PlanType
import org.springframework.stereotype.Component

@Component
class MergePolicyValidator {
    fun validate(userPlan: PlanType, mergeType: MergeType) {
        if (userPlan == PlanType.FREE) {
            throw IllegalArgumentException("Free 플랜에서는 머지 기능을 사용할 수 없습니다. 플랜을 업그레이드해 주세요.")
        }

        when (mergeType) {
            MergeType.SQUASH -> Unit
            MergeType.DEEP -> {
                if (userPlan == PlanType.STANDARD) {
                    throw IllegalArgumentException("Deep Merge는 Master 플랜 전용 기능입니다. 플랜을 업그레이드해 주세요.")
                }
            }
            MergeType.FAST_FORWARD -> {
                throw IllegalArgumentException("Fast-Forward 머지는 더 이상 지원되지 않습니다. Squash 또는 Deep Merge를 사용해 주세요.")
            }
            MergeType.NONE -> {
                throw IllegalArgumentException("머지 타입을 선택해 주세요.")
            }
        }
    }
}
