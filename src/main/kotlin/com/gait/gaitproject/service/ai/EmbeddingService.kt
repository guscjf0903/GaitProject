package com.gait.gaitproject.service.ai

import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

interface EmbeddingService {
    fun embed(text: String): List<Float>?
    fun dimension(): Int
    fun isAvailable(): Boolean
}

@Service
class DefaultEmbeddingService(
    @Autowired(required = false) private val embeddingModel: EmbeddingModel?
) : EmbeddingService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun embed(text: String): List<Float>? {
        val model = embeddingModel ?: return null
        if (text.isBlank()) return null
        return try {
            val truncated = if (text.length > 8000) text.take(8000) else text
            model.embed(truncated).toList()
        } catch (e: Exception) {
            logger.warn("Embedding generation failed: {}", e.message)
            null
        }
    }

    override fun dimension(): Int = 1536

    override fun isAvailable(): Boolean = embeddingModel != null
}
