package com.gait.gaitproject.service.ai

import com.gait.gaitproject.domain.common.enums.PlanType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 플랜별 AI 서비스 라우터 (설계서 4.1, 5.1 기준)
 * 
 * - FREE: Gemini Flash
 * - STANDARD: Gemini Flash (프리미엄 지갑 사용 시 프리미엄 모델 가능)
 * - MASTER: Gemini Pro / GPT-4o
 */
@Service
class AiRouter(
    @Autowired(required = false) private val stubAiService: StubAiService?,
    @Autowired(required = false) private val geminiFlashService: GeminiFlashService?,
    @Autowired(required = false) private val geminiProService: GeminiProService?
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun pick(planType: PlanType): AiService {
        return when (planType) {
            PlanType.FREE -> {
                geminiFlashService ?: stubAiService ?: throw IllegalStateException(
                    "AI 서비스가 설정되지 않았습니다. Gemini Flash 또는 Stub 서비스를 설정하세요."
                )
            }
            PlanType.STANDARD -> {
                // STANDARD: 기본은 Flash, 프리미엄 지갑 사용 시 Pro 가능 (향후 구현)
                geminiFlashService ?: stubAiService ?: throw IllegalStateException(
                    "AI 서비스가 설정되지 않았습니다."
                )
            }
            PlanType.MASTER -> {
                // MASTER: Pro 우선, 없으면 Flash
                geminiProService ?: geminiFlashService ?: stubAiService ?: throw IllegalStateException(
                    "AI 서비스가 설정되지 않았습니다."
                )
            }
        }.also {
            logger.debug("AI 서비스 선택: plan=$planType, service=${it.javaClass.simpleName}")
        }
    }
}


