package com.gait.gaitproject.service.rag

import com.gait.gaitproject.domain.workspace.repository.BranchRepository
import com.gait.gaitproject.domain.workspace.repository.CommitVectorRepository
import com.gait.gaitproject.domain.workspace.repository.CommitVectorSearchResult
import com.gait.gaitproject.service.ai.EmbeddingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

data class RagSearchResult(
    val items: List<CommitVectorSearchResult>,
    val formattedContext: String,
    val searchDurationMs: Long,
    val itemCount: Int
)

@Service
class RagService(
    private val embeddingService: EmbeddingService,
    private val commitVectorRepository: CommitVectorRepository,
    private val branchRepository: BranchRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun search(
        query: String,
        branchId: UUID,
        tokenBudget: Int,
        limit: Int = 5
    ): RagSearchResult {
        val start = System.currentTimeMillis()

        val branch = branchRepository.findById(branchId).orElse(null)
            ?: return emptyResult(start)
        val headCommitId = branch.headCommit?.id
            ?: return emptyResult(start)

        val ancestorIds = commitVectorRepository.findAncestorIds(headCommitId)
        if (ancestorIds.isEmpty()) return emptyResult(start)

        if (commitVectorRepository.countWithEmbedding(ancestorIds) == 0) {
            return emptyResult(start)
        }

        val queryEmbedding = embeddingService.embed(query)
        if (queryEmbedding == null) {
            logger.warn("Failed to embed query for RAG search")
            return emptyResult(start)
        }

        val results = commitVectorRepository.searchSimilar(queryEmbedding, ancestorIds, limit)
        val formatted = RagContextFormatter.format(results, tokenBudget)
        val duration = System.currentTimeMillis() - start

        logger.debug("RAG search completed: {} results in {}ms", results.size, duration)

        return RagSearchResult(
            items = results,
            formattedContext = formatted,
            searchDurationMs = duration,
            itemCount = results.size
        )
    }

    private fun emptyResult(startMs: Long) = RagSearchResult(
        items = emptyList(),
        formattedContext = "",
        searchDurationMs = System.currentTimeMillis() - startMs,
        itemCount = 0
    )
}
