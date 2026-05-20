package com.nexusai.gateway.rag;

import com.nexusai.gateway.rag.dto.SimilarChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Handles all pgvector operations via JdbcTemplate.
 * JPA/Hibernate does not support the 'vector' type natively, so we bypass it
 * for write/search operations and let JPA handle everything else.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStore {

    private final JdbcTemplate jdbcTemplate;

    public void saveEmbedding(UUID chunkId, float[] embedding) {
        String vectorStr = toVectorString(embedding);
        int updated = jdbcTemplate.update(
                "UPDATE document_chunks SET embedding = ?::vector WHERE id = ?::uuid",
                vectorStr, chunkId.toString()
        );
        if (updated == 0) {
            log.warn("No chunk found with id={} when saving embedding", chunkId);
        }
    }

    /**
     * Finds the most similar chunks to the query embedding using cosine distance.
     * Returns results ordered by similarity descending (most relevant first).
     *
     * @param tenantId      restricts results to this tenant
     * @param queryEmbedding the embedding of the user's query
     * @param topK          max number of results
     */
    public List<SimilarChunk> findSimilar(UUID tenantId, float[] queryEmbedding, int topK) {
        String vectorStr = toVectorString(queryEmbedding);

        // cosine distance (<=>) gives 0 = identical, 2 = opposite
        // converting to similarity: 1 - distance puts it in a more intuitive (-1, 1) range
        // For large tenants, the IVFFlat index in V4 migration speeds this up significantly
        return jdbcTemplate.query(
                """
                SELECT content,
                       document_name,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM document_chunks
                WHERE tenant_id = ?::uuid
                  AND embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> new SimilarChunk(
                        rs.getString("content"),
                        rs.getString("document_name"),
                        rs.getDouble("similarity")
                ),
                vectorStr, tenantId.toString(), vectorStr, topK
        );
    }

    /**
     * Converts a float array to pgvector string format: [0.1,0.2,...,0.n]
     */
    private String toVectorString(float[] embedding) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
