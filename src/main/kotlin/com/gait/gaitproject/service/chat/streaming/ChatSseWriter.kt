package com.gait.gaitproject.service.chat.streaming

import com.fasterxml.jackson.databind.ObjectMapper
import com.gait.gaitproject.dto.chat.SseEvent
import com.gait.gaitproject.dto.common.ApiResponse
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class ChatSseWriter(
    private val out: OutputStream,
    private val objectMapper: ObjectMapper,
) {
    fun openConnection() {
        out.write(": connected\n\n".toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    fun sendChatEvent(eventName: String, data: Any?) {
        sendEvent(eventName, ApiResponse.ok(SseEvent(type = eventName, data = data)))
    }

    fun sendError(message: String, errorType: String, workspaceId: Any, branchId: Any) {
        sendChatEvent(
            eventName = "ANSWER_ERROR",
            data = mapOf(
                "message" to message,
                "errorType" to errorType,
                "workspaceId" to workspaceId,
                "branchId" to branchId,
            ),
        )
    }

    private fun sendEvent(eventName: String, payload: Any) {
        val json = objectMapper.writeValueAsString(payload)
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
}
