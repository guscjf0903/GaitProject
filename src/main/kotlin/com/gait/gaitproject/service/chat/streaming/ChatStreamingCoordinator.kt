package com.gait.gaitproject.service.chat.streaming

import com.fasterxml.jackson.databind.ObjectMapper
import com.gait.gaitproject.domain.common.enums.MessageRole
import com.gait.gaitproject.dto.chat.ChatStreamRequest
import com.gait.gaitproject.dto.chat.MessageSendRequest
import com.gait.gaitproject.service.access.AccessGuard
import com.gait.gaitproject.service.ai.AiRouter
import com.gait.gaitproject.service.chat.ContextBuilder
import com.gait.gaitproject.service.chat.MessageService
import com.gait.gaitproject.service.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.OutputStream
import java.util.UUID

@Service
class ChatStreamingCoordinator(
    private val accessGuard: AccessGuard,
    private val contextBuilder: ContextBuilder,
    private val userService: UserService,
    private val messageService: MessageService,
    private val aiRouter: AiRouter,
    private val chatRagOrchestrator: ChatRagOrchestrator,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun stream(request: ChatStreamRequest, authenticatedUserId: UUID, out: OutputStream) {
        val workspaceId = requireNotNull(request.workspaceId)
        val branchId = requireNotNull(request.branchId)
        accessGuard.requireBranchAccess(workspaceId, branchId, authenticatedUserId)

        val sseWriter = ChatSseWriter(out, objectMapper)
        sseWriter.openConnection()

        try {
            val planType = userService.get(authenticatedUserId).plan
            val ragContext = chatRagOrchestrator.resolve(
                query = request.content,
                branchId = branchId,
                planType = planType,
                authenticatedUserId = authenticatedUserId,
                sseWriter = sseWriter,
            )

            val builtContext = contextBuilder.build(
                branchId = branchId,
                contextCommitId = request.contextCommitId,
                userQuery = request.content,
                planType = planType,
                ragSection = ragContext.ragSection,
            )

            messageService.send(
                request = MessageSendRequest(
                    workspaceId = workspaceId,
                    branchId = branchId,
                    userId = authenticatedUserId,
                    role = MessageRole.USER,
                    content = request.content,
                    rawPrompt = builtContext.combined,
                ),
                authenticatedUserId = authenticatedUserId,
            )

            val aiService = aiRouter.pick(planType)
            val aiResult = aiService.streamAnswer(builtContext.combined) { chunk ->
                sseWriter.sendChatEvent(
                    eventName = "ANSWER_CHUNK",
                    data = mapOf(
                        "chunk" to chunk,
                        "workspaceId" to workspaceId,
                        "branchId" to branchId,
                    ),
                )
            }

            messageService.send(
                request = MessageSendRequest(
                    workspaceId = workspaceId,
                    branchId = branchId,
                    userId = authenticatedUserId,
                    role = MessageRole.ASSISTANT,
                    content = aiResult.fullResponse,
                    rawResponse = aiResult.fullResponse,
                    inputTokens = aiResult.promptTokens,
                    outputTokens = aiResult.completionTokens,
                    totalTokens = aiResult.totalTokens,
                    modelName = aiResult.modelName,
                ),
                authenticatedUserId = authenticatedUserId,
            )

            sseWriter.sendChatEvent(
                eventName = "ANSWER_DONE",
                data = mapOf(
                    "workspaceId" to workspaceId,
                    "branchId" to branchId,
                    "totalTokens" to aiResult.totalTokens,
                    "ragUsed" to (ragContext.ragSection != null),
                ),
            )
        } catch (exception: Exception) {
            log.warn("Chat stream error: {}", exception.message, exception)
            try {
                sseWriter.sendError(
                    message = exception.message ?: "Unknown error",
                    errorType = exception.javaClass.simpleName,
                    workspaceId = workspaceId,
                    branchId = branchId,
                )
            } catch (_: Exception) {
                // Client connection may already be closed.
            }
        }
    }
}
