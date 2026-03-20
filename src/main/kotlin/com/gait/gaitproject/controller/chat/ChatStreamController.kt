package com.gait.gaitproject.controller.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.gait.gaitproject.dto.chat.ChatStreamRequest
import com.gait.gaitproject.dto.chat.MessageSendRequest
import com.gait.gaitproject.dto.chat.SseEvent
import com.gait.gaitproject.dto.common.ApiResponse
import com.gait.gaitproject.service.ai.AiRouter
import com.gait.gaitproject.service.chat.ContextBuilder
import com.gait.gaitproject.service.chat.MessageService
import com.gait.gaitproject.service.rag.RagQuotaService
import com.gait.gaitproject.service.rag.RagRouteType
import com.gait.gaitproject.service.rag.RagRouter
import com.gait.gaitproject.service.rag.RagService
import com.gait.gaitproject.service.user.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as OasApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import com.gait.gaitproject.domain.common.enums.PlanType
import com.gait.gaitproject.domain.common.enums.MessageRole
import com.gait.gaitproject.security.UserPrincipal
import java.nio.charset.StandardCharsets

@Tag(name = "Chat Stream", description = "SSE 기반 스트리밍 응답")
@RestController
@RequestMapping("/api/chat")
class ChatStreamController(
    private val contextBuilder: ContextBuilder,
    private val userService: UserService,
    private val messageService: MessageService,
    private val aiRouter: AiRouter,
    private val ragRouter: RagRouter,
    private val ragService: RagService,
    private val ragQuotaService: RagQuotaService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.ai.rag.token-budget:2000}") private val ragTokenBudget: Int,
    @Value("\${app.ai.rag.max-results:5}") private val ragMaxResults: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping(
        value = ["/stream"],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    @Operation(
        summary = "AI 답변 SSE 스트리밍",
        description = "응답은 `text/event-stream`으로 전송됩니다. 이벤트: `RAG_STATUS`(검색 시) → `ANSWER_CHUNK`(여러 번) → `ANSWER_DONE`(1번)"
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
    ): ResponseEntity<StreamingResponseBody> {
        val workspaceId = requireNotNull(request.workspaceId)
        val branchId = requireNotNull(request.branchId)
        val userId = principal?.userId

        val headers = HttpHeaders().apply {
            contentType = MediaType.TEXT_EVENT_STREAM
            set("Cache-Control", "no-cache, no-transform")
            set("X-Accel-Buffering", "no")
        }

        val body = StreamingResponseBody { out ->
            fun sendSse(eventName: String, payloadObj: Any) {
                val json = objectMapper.writeValueAsString(payloadObj)
                val bytes = buildString {
                    append("event: ")
                    append(eventName)
                    append('\n')
                    append("data: ")
                    append(json)
                    append('\n')
                    append('\n')
                }.toByteArray(StandardCharsets.UTF_8)
                out.write(bytes)
                out.flush()
            }

            try {
                out.write(": connected\n\n".toByteArray(StandardCharsets.UTF_8))
                out.flush()

                val plan = userId?.let { userService.get(it).plan } ?: PlanType.FREE

                // --- RAG 라우팅 및 검색 ---
                var ragSection: String? = null
                val ragRoute = ragRouter.route(request.content, plan)

                if (ragRoute.type == RagRouteType.EXTRACTION_SEARCH && userId != null) {
                    val quota = ragQuotaService.check(userId, plan)
                    if (quota.allowed) {
                        val ragResult = ragService.search(
                            query = request.content,
                            branchId = branchId,
                            tokenBudget = ragTokenBudget,
                            limit = ragMaxResults
                        )
                        if (ragResult.itemCount > 0) {
                            ragSection = ragResult.formattedContext
                            ragQuotaService.deduct(userId, branchId)
                        }
                        val ragStatusPayload: Map<String, Any?> = mapOf(
                            "searched" to true,
                            "itemCount" to ragResult.itemCount,
                            "searchDurationMs" to ragResult.searchDurationMs,
                            "routeType" to ragRoute.type.name,
                            "remainingCalls" to quota.remainingCalls
                        )
                        sendSse("RAG_STATUS", ApiResponse.ok(SseEvent(type = "RAG_STATUS", data = ragStatusPayload)))
                    }
                }

                // 컨텍스트 조립 (토큰 예산 기반 + RAG 결과)
                val ctx = contextBuilder.build(
                    branchId = branchId,
                    contextCommitId = request.contextCommitId,
                    userQuery = request.content,
                    planType = plan,
                    ragSection = ragSection
                )

                // USER 메시지 저장
                messageService.send(
                    MessageSendRequest(
                        workspaceId = workspaceId,
                        branchId = branchId,
                        userId = userId,
                        role = MessageRole.USER,
                        content = request.content,
                        rawPrompt = ctx.combined
                    )
                )

                // 플랜별 AI 서비스 선택 + 스트리밍
                val ai = aiRouter.pick(plan)
                val aiResult = ai.streamAnswer(ctx.combined) { chunk ->
                    val payload: Map<String, Any> = mapOf(
                        "chunk" to chunk,
                        "workspaceId" to workspaceId,
                        "branchId" to branchId
                    )
                    sendSse("ANSWER_CHUNK", ApiResponse.ok(SseEvent(type = "ANSWER_CHUNK", data = payload)))
                }

                // ASSISTANT 메시지 저장
                messageService.send(
                    MessageSendRequest(
                        workspaceId = workspaceId,
                        branchId = branchId,
                        userId = userId,
                        role = MessageRole.ASSISTANT,
                        content = aiResult.fullResponse,
                        rawResponse = aiResult.fullResponse,
                        inputTokens = aiResult.promptTokens,
                        outputTokens = aiResult.completionTokens,
                        totalTokens = aiResult.totalTokens,
                        modelName = aiResult.modelName
                    )
                )

                // DONE 이벤트: 토큰 + RAG 정보
                val donePayload: Map<String, Any?> = mapOf(
                    "workspaceId" to workspaceId,
                    "branchId" to branchId,
                    "totalTokens" to aiResult.totalTokens,
                    "ragUsed" to (ragSection != null)
                )
                sendSse("ANSWER_DONE", ApiResponse.ok(SseEvent(type = "ANSWER_DONE", data = donePayload)))
            } catch (e: Exception) {
                log.warn("Chat stream error: {}", e.message, e)
                try {
                    val payload: Map<String, Any?> = mapOf(
                        "message" to (e.message ?: "Unknown error"),
                        "errorType" to e.javaClass.simpleName,
                        "workspaceId" to workspaceId,
                        "branchId" to branchId
                    )
                    sendSse("ANSWER_ERROR", ApiResponse.ok(SseEvent(type = "ANSWER_ERROR", data = payload)))
                } catch (_: Exception) {
                    // client already closed
                }
            }
        }

        return ResponseEntity.ok().headers(headers).body(body)
    }
}
