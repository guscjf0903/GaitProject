package com.gait.gaitproject.controller.chat

import com.gait.gaitproject.dto.chat.ChatStreamRequest
import com.gait.gaitproject.dto.chat.SseEvent
import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.service.ai.AiRouter
import com.gait.gaitproject.service.chat.ContextBuilder
import com.gait.gaitproject.service.rag.RagInterceptor
import com.gait.gaitproject.service.user.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as OasApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.core.task.TaskExecutor
import java.util.UUID
import com.gait.gaitproject.domain.common.enums.PlanType
import com.gait.gaitproject.security.UserPrincipal

@Tag(name = "Chat Stream", description = "SSE 기반 스트리밍 응답")
@RestController
@RequestMapping("/api/chat")
class ChatStreamController(
    private val contextBuilder: ContextBuilder,
    private val userService: UserService,
    private val aiRouter: AiRouter,
    private val ragInterceptor: RagInterceptor,
    private val taskExecutor: TaskExecutor
) {

    /**
     * AI 답변 SSE 스트리밍
     * 설계서 4.2 기준: 동적 컨텍스트 조립 + 플랜별 AI 서비스 선택
     */
    @PostMapping(
        value = ["/stream"],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    @Operation(
        summary = "AI 답변 SSE 스트리밍",
        description = "응답은 `text/event-stream`으로 전송됩니다. 이벤트명은 `ANSWER_CHUNK`(여러 번) → `ANSWER_DONE`(1번) 순서입니다."
    )
    @ApiResponses(
        value = [
            OasApiResponse(
                responseCode = "200",
                description = "SSE 스트림 시작",
                content = [Content(mediaType = "text/event-stream", schema = Schema(implementation = com.gait.gaitproject.dto.common.ApiResponse::class))]
            )
        ]
    )
    fun stream(
        @Valid @RequestBody request: ChatStreamRequest,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): SseEmitter {
        val workspaceId = requireNotNull(request.workspaceId)
        val branchId = requireNotNull(request.branchId)
        val userId = principal?.userId

        val emitter = SseEmitter(120_000L) // 평균 30초, 최악 90초 정도를 고려한 타임아웃

        taskExecutor.execute {
            try {
                // 플랜 조회 (JWT principal에서 userId 추출)
                val plan = userId?.let { userService.get(it).plan } ?: PlanType.FREE

                // RAG 검색 (필요 시)
                val rag = userId?.let { ragInterceptor.maybeUseRag(it, branchId, request.content) }
                val injected = rag?.injectedText?.let { "\n\n$it\n" } ?: ""

                // 컨텍스트 조립 (토큰 예산 기반)
                val ctx = contextBuilder.build(
                    branchId = branchId,
                    contextCommitId = request.contextCommitId,
                    userQuery = request.content + injected,
                    planType = plan
                )

                // 플랜별 AI 서비스 선택
                val ai = aiRouter.pick(plan)

                // 스트리밍 응답 생성
                ai.streamAnswer(ctx.combined) { chunk ->
                    val payload: Map<String, Any> = mapOf(
                        "chunk" to chunk,
                        "workspaceId" to workspaceId,
                        "branchId" to branchId
                    )
                    emitter.send(
                        SseEmitter.event()
                            .name("ANSWER_CHUNK")
                            .data(ApiResponse.ok(SseEvent(type = "ANSWER_CHUNK", data = payload)))
                    )
                }

                emitter.send(
                    SseEmitter.event()
                        .name("ANSWER_DONE")
                        .data(ApiResponse.ok(SseEvent(type = "ANSWER_DONE", data = null)))
                )
                emitter.complete()
            } catch (e: Exception) {
                try {
                    val payload: Map<String, Any?> = mapOf(
                        "message" to (e.message ?: "Unknown error"),
                        "errorType" to e.javaClass.simpleName,
                        "workspaceId" to workspaceId,
                        "branchId" to branchId
                    )
                    emitter.send(
                        SseEmitter.event()
                            .name("ANSWER_ERROR")
                            .data(ApiResponse.ok(SseEvent(type = "ANSWER_ERROR", data = payload)))
                    )
                } catch (_: Exception) {
                    // ignore: 클라이언트가 이미 끊긴 경우 등
                } finally {
                    emitter.complete()
                }
            }
        }

        return emitter
    }
}


