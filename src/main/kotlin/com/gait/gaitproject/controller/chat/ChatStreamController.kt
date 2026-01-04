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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.core.task.TaskExecutor
import java.util.UUID
import com.gait.gaitproject.domain.common.enums.PlanType

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
     * 1단계: SSE 스트리밍 파이프라인 뼈대만 제공합니다.
     * 실제 LLM/RAG/ContextBuilder는 이후 단계에서 연결합니다.
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
    fun stream(@Valid @RequestBody request: ChatStreamRequest): SseEmitter {
        val workspaceId = requireNotNull(request.workspaceId)
        val branchId = requireNotNull(request.branchId)

        // MVP: user는 일단 workspace 소유자의 첫 user로 가정(2단계 이후 JWT principal로 대체)
        val userId: UUID? = null
        val emitter = SseEmitter(120_000L) // 평균 30초, 최악 90초 정도를 고려한 타임아웃

        taskExecutor.execute {
            try {
                // userId가 없으면 임시로 FREE 가정
                val plan = userId?.let { userService.get(it).plan } ?: PlanType.FREE
                val rag = userId?.let { ragInterceptor.maybeUseRag(it, branchId, request.content) }
                val injected = rag?.injectedText?.let { "\n\n$it\n" } ?: ""
                val ctx = contextBuilder.build(branchId = branchId, userQuery = request.content + injected)
                val ai = aiRouter.pick(plan)
                val prompt = ctx.combined

                ai.streamAnswer(prompt) { chunk ->
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
                emitter.completeWithError(e)
            }
        }

        return emitter
    }
}


