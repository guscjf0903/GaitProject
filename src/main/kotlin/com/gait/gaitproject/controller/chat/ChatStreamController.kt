package com.gait.gaitproject.controller.chat

import com.gait.gaitproject.dto.chat.ChatStreamRequest
import com.gait.gaitproject.service.chat.streaming.ChatStreamingCoordinator
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as OasApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import com.gait.gaitproject.security.UserPrincipal

@Tag(name = "Chat Stream", description = "SSE 기반 스트리밍 응답")
@RestController
@RequestMapping("/api/chat")
class ChatStreamController(
    private val chatStreamingCoordinator: ChatStreamingCoordinator,
) {
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
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<StreamingResponseBody> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.TEXT_EVENT_STREAM
            set("Cache-Control", "no-cache, no-transform")
            set("X-Accel-Buffering", "no")
        }

        val body = StreamingResponseBody { out ->
            chatStreamingCoordinator.stream(request, principal.userId, out)
        }

        return ResponseEntity.ok().headers(headers).body(body)
    }
}
