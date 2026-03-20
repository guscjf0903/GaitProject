package com.gait.gaitproject.domain.workspace.repository

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

data class CommitVectorSearchResult(
    val commitId: UUID,
    val keyPoint: String?,
    val shortSummary: String?,
    val longSummary: String?,
    val distance: Double
)

@Repository
class CommitVectorRepository(
    private val jdbc: NamedParameterJdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun saveEmbedding(commitId: UUID, embedding: List<Float>) {
        try {
            val vector = embedding.joinToString(",", "[", "]")
            jdbc.update(
                "UPDATE commits SET embedding = CAST(:vec AS vector) WHERE id = :id",
                mapOf("vec" to vector, "id" to commitId)
            )
        } catch (e: Exception) {
            logger.warn("Failed to save embedding for commit {}: {}", commitId, e.message)
        }
    }

    fun findAncestorIds(headCommitId: UUID): List<UUID> {
        val sql = """
            WITH RECURSIVE ancestors AS (
                SELECT id, parent_id FROM commits WHERE id = :head AND deleted_at IS NULL
                UNION ALL
                SELECT c.id, c.parent_id
                FROM commits c
                JOIN ancestors a ON c.id = a.parent_id
                WHERE c.deleted_at IS NULL
            )
            SELECT id FROM ancestors
        """.trimIndent()

        return jdbc.queryForList(sql, mapOf("head" to headCommitId), UUID::class.java)
    }

    fun countWithEmbedding(commitIds: List<UUID>): Int {
        if (commitIds.isEmpty()) return 0
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM commits WHERE id IN (:ids) AND embedding IS NOT NULL AND deleted_at IS NULL",
            mapOf("ids" to commitIds),
            Int::class.java
        ) ?: 0
    }

    fun searchSimilar(
        queryEmbedding: List<Float>,
        scopeCommitIds: List<UUID>,
        limit: Int = 5
    ): List<CommitVectorSearchResult> {
        if (scopeCommitIds.isEmpty()) return emptyList()

        val vector = queryEmbedding.joinToString(",", "[", "]")
        val sql = """
            SELECT id, key_point, short_summary, long_summary,
                   embedding <=> CAST(:query AS vector) AS distance
            FROM commits
            WHERE id IN (:ids)
              AND embedding IS NOT NULL
              AND deleted_at IS NULL
            ORDER BY distance
            LIMIT :lim
        """.trimIndent()

        return try {
            jdbc.query(
                sql,
                mapOf("query" to vector, "ids" to scopeCommitIds, "lim" to limit)
            ) { rs, _ ->
                CommitVectorSearchResult(
                    commitId = UUID.fromString(rs.getString("id")),
                    keyPoint = rs.getString("key_point"),
                    shortSummary = rs.getString("short_summary"),
                    longSummary = rs.getString("long_summary"),
                    distance = rs.getDouble("distance")
                )
            }
        } catch (e: Exception) {
            logger.warn("Vector search failed: {}", e.message)
            emptyList()
        }
    }
}
