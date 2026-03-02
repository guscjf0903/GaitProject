package com.gait.gaitproject.controller.chat

import com.gait.gaitproject.dto.chat.ChatStreamRequest
import com.gait.gaitproject.dto.chat.MessageSendRequest
import com.gait.gaitproject.dto.chat.SseEvent
import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.service.ai.AiRouter
import com.gait.gaitproject.service.chat.ContextBuilder
import com.gait.gaitproject.service.chat.MessageService
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
import com.gait.gaitproject.domain.common.enums.MessageRole
import com.gait.gaitproject.security.UserPrincipal

@Tag(name = "Chat Stream", description = "SSE 기반 스트리밍 응답")
@RestController
@RequestMapping("/api/chat")
class ChatStreamController(
    private val contextBuilder: ContextBuilder,
    private val userService: UserService,
    private val messageService: MessageService,
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

                // [백엔드 주도 저장 1] 프롬프트 조립이 끝났으므로, 사용자의 메시지를 서버에서 직접 DB에 저장합니다.
                messageService.send(
                    MessageSendRequest(
                        workspaceId = workspaceId,
                        branchId = branchId,
                        userId = userId,
                        role = MessageRole.USER,
                        content = request.content,
                        rawPrompt = ctx.combined // AI에게 보낼 전체 프롬프트를 USER 기록에 남김
                    )
                )

                // 플랜별 AI 서비스 선택
                val ai = aiRouter.pick(plan)

                // 스트리밍 응답 생성
                val aiResult = ai.streamAnswer(ctx.combined) { chunk ->
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

                // [백엔드 주도 저장 2] AI 응답이 끝났으므로, AI의 메시지를 서버에서 직접 DB에 저장합니다.
                messageService.send(
                    MessageSendRequest(
                        workspaceId = workspaceId,
                        branchId = branchId,
                        userId = userId,
                        role = MessageRole.ASSISTANT,
                        content = aiResult.fullResponse, // 현재는 raw와 동일하지만 필요시 정제 로직 추가 가능
                        rawResponse = aiResult.fullResponse,
                        inputTokens = aiResult.promptTokens,
                        outputTokens = aiResult.completionTokens,
                        totalTokens = aiResult.totalTokens,
                        modelName = aiResult.modelName
                    )
                )

                // 프론트엔드 알림용 토큰 정보만 전송 (rawPrompt 등은 더 이상 프론트로 보낼 필요 없음)
                val donePayload: Map<String, Any?> = mapOf(
                    "workspaceId" to workspaceId,
                    "branchId" to branchId,
                    "totalTokens" to aiResult.totalTokens
                )

                emitter.send(
                    SseEmitter.event()
                        .name("ANSWER_DONE")
                        .data(ApiResponse.ok(SseEvent(type = "ANSWER_DONE", data = donePayload)))
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


