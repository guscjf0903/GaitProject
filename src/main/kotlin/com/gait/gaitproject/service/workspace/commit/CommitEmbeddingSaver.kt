package com.gait.gaitproject.service.workspace.commit

import com.gait.gaitproject.domain.workspace.entity.Commit
import com.gait.gaitproject.domain.workspace.repository.CommitVectorRepository
import com.gait.gaitproject.service.ai.EmbeddingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CommitEmbeddingSaver(
    private val embeddingService: EmbeddingService,
    private val commitVectorRepository: CommitVectorRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun save(commit: Commit) {
        if (!embeddingService.isAvailable()) return

        try {
            val textToEmbed = commit.longSummary ?: commit.shortSummary ?: commit.keyPoint
            val embedding = embeddingService.embed(textToEmbed) ?: return
            commitVectorRepository.saveEmbedding(commit.id!!, embedding)
            logger.debug("Saved embedding for commit {}", commit.id)
        } catch (exception: Exception) {
            logger.warn("Failed to save commit embedding: {}", exception.message)
        }
    }
}
