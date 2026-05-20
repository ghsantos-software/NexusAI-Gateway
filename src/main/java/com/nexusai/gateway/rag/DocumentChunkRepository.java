package com.nexusai.gateway.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    boolean existsByTenantId(UUID tenantId);

    void deleteAllByTenantIdAndDocumentName(UUID tenantId, String documentName);

    @Query("SELECT DISTINCT d.documentName FROM DocumentChunk d WHERE d.tenantId = :tenantId ORDER BY d.documentName")
    List<String> findDistinctDocumentNamesByTenantId(@Param("tenantId") UUID tenantId);
}
