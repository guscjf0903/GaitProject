package com.gait.gaitproject.service.ai

import com.gait.gaitproject.domain.common.enums.PlanType
import org.springframework.stereotype.Service

@Service
class AiRouter(
    private val stubAiService: StubAiService
) {
    fun pick(planType: PlanType): AiService {
        // MVP: 플랜별로 서비스만 분기 구조를 먼저 고정
        return when (planType) {
            PlanType.FREE -> stubAiService
            PlanType.STANDARD -> stubAiService
            PlanType.MASTER -> stubAiService
        }
    }
}


