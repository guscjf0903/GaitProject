package com.gait.gaitproject.service.chat.streaming

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatSseWriterTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `openConnection writes sse prelude`() {
        val out = ByteArrayOutputStream()

        ChatSseWriter(out, objectMapper).openConnection()

        assertEquals(": connected\n\n", out.toString(StandardCharsets.UTF_8))
    }

    @Test
    fun `sendChatEvent writes named sse envelope`() {
        val out = ByteArrayOutputStream()
        val writer = ChatSseWriter(out, objectMapper)

        writer.sendChatEvent(
            eventName = "ANSWER_CHUNK",
            data = mapOf("chunk" to "hello"),
        )

        val payload = out.toString(StandardCharsets.UTF_8)
        assertTrue(payload.startsWith("event: ANSWER_CHUNK\n"))
        assertTrue(payload.contains("\"code\":\"OK\""))
        assertTrue(payload.contains("\"type\":\"ANSWER_CHUNK\""))
        assertTrue(payload.contains("\"chunk\":\"hello\""))
        assertTrue(payload.endsWith("\n\n"))
    }

    @Test
    fun `sendError emits answer error payload`() {
        val out = ByteArrayOutputStream()
        val writer = ChatSseWriter(out, objectMapper)

        writer.sendError(
            message = "권한이 없습니다.",
            errorType = "ForbiddenException",
            workspaceId = "workspace-1",
            branchId = "branch-1",
        )

        val payload = out.toString(StandardCharsets.UTF_8)
        assertTrue(payload.startsWith("event: ANSWER_ERROR\n"))
        assertTrue(payload.contains("\"errorType\":\"ForbiddenException\""))
        assertTrue(payload.contains("\"workspaceId\":\"workspace-1\""))
        assertTrue(payload.contains("\"branchId\":\"branch-1\""))
    }
}
