package com.nexusai.gateway.rag;

import com.nexusai.gateway.shared.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores a chunk of text from an uploaded document.
 * The 'embedding' column (vector(1536)) exists in the DB but is managed
 * directly via JdbcTemplate in VectorStore — Hibernate does not map it.
 */
@Entity
@Table(name = "document_chunks")
@Getter
@Setter
@NoArgsConstructor
public class DocumentChunk extends TenantAwareEntity {

    @Column(nullable = false, length = 200)
    private String documentName;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public DocumentChunk(String documentName, int chunkIndex, String content) {
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }
}
