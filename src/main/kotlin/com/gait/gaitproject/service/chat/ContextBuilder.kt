package com.gait.gaitproject.service.chat

import com.gait.gaitproject.domain.chat.repository.MessageRepository
import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.service.common.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class BuiltContext(
    val systemPrompt: String,
    val recentMessages: List<String>,
    val headLongSummary: String?,
    val lineageSummaries: List<String>,
    val combined: String
)

@Service
@Transactional(readOnly = true)
class ContextBuilder(
    private val branchRepository: BranchRepository,
    private val messageRepository: MessageRepository
) {
    /**
     * 3단계(MVP): 토큰 예산은 우선 “문자수 기반”으로 근사합니다.
     */
    fun build(branchId: UUID, userQuery: String, recentLimit: Int = 20, lineageLimit: Int = 10): BuiltContext {
        val branch = branchRepository.findById(branchId).orElseThrow {
            NotFoundException("Branch not found. id=$branchId")
        }

        val system = "You are a helpful AI assistant. Follow project rules."

        val recent = messageRepository
            .findByBranch_IdAndCommitIsNullAndDeletedAtIsNullOrderBySequenceDesc(branchId, PageRequest.of(0, recentLimit))
            .asReversed() // ASC로 보이도록
            .map { "${it.role}: ${it.content}" }

        val head = branch.headCommit?.longSummary

        val lineage = buildLineage(branch.headCommit, lineageLimit)

        val combined = buildString {
            appendLine("[SYSTEM]")
            appendLine(system)
            appendLine()
            appendLine("[HEAD_LONG_SUMMARY]")
            appendLine(head ?: "")
            appendLine()
            appendLine("[LINEAGE]")
            lineage.forEach { appendLine(it) }
            appendLine()
            appendLine("[RECENT]")
            recent.forEach { appendLine(it) }
            appendLine()
            appendLine("[USER_QUERY]")
            appendLine(userQuery)
        }

        return BuiltContext(
            systemPrompt = system,
            recentMessages = recent,
            headLongSummary = head,
            lineageSummaries = lineage,
            combined = combined
        )
    }

    private fun buildLineage(head: Commit?, max: Int): List<String> {
        val out = mutableListOf<String>()
        var cur = head?.parent
        while (cur != null && out.size < max) {
            val text = cur.shortSummary ?: cur.keyPoint
            out += "- ${cur.id}: $text"
            cur = cur.parent
        }
        return out
    }
}


