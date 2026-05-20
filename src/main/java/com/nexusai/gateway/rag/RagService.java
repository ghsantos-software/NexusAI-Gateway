package com.nexusai.gateway.rag;

import com.nexusai.gateway.rag.dto.SimilarChunk;
import com.nexusai.gateway.rag.dto.UploadResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final DocumentChunkRepository chunkRepository;
    private final VectorStore vectorStore;
    private final ChunkingService chunkingService;
    private final EmbeddingModel embeddingModel;

    // Chunks below this cosine similarity score are ignored as irrelevant
    private static final double SIMILARITY_THRESHOLD = 0.35;
    private static final int DEFAULT_TOP_K = 3;
    // TODO: add .docx support via Apache POI when needed
    private static final List<String> SUPPORTED_TYPES = List.of(".txt", ".pdf");

    @Transactional
    public UploadResponse processDocument(MultipartFile file, UUID tenantId) {
        validateFile(file);

        String filename = file.getOriginalFilename();
        String text = extractText(file);
        List<String> chunks = chunkingService.chunk(text);

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document is empty or could not be parsed: " + filename);
        }

        log.info("Processing '{}': {} chunks for tenant={}", filename, chunks.size(), tenantId);

        int embeddedCount = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);

            var chunk = new DocumentChunk(filename, i, content);
            chunk.setTenantId(tenantId);
            var saved = chunkRepository.save(chunk);

            try {
                float[] embedding = embeddingModel.embed(content);
                vectorStore.saveEmbedding(saved.getId(), embedding);
                embeddedCount++;
            } catch (Exception e) {
                // Embedding failure for one chunk should not abort the whole upload
                log.warn("Failed to embed chunk {}/{} of '{}': {}", i + 1, chunks.size(), filename, e.getMessage());
            }
        }

        log.info("Document '{}' processed: {}/{} chunks embedded", filename, embeddedCount, chunks.size());
        return new UploadResponse(filename, chunks.size(), embeddedCount);
    }

    /**
     * Searches for chunks semantically similar to the query.
     * Returns null if the tenant has no documents.
     */
    @Transactional(readOnly = true)
    public String findRelevantContext(String query, UUID tenantId) {
        if (!chunkRepository.existsByTenantId(tenantId)) {
            return null;
        }

        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingModel.embed(query);
        } catch (Exception e) {
            log.warn("Could not generate query embedding, skipping RAG: {}", e.getMessage());
            return null;
        }

        var similar = vectorStore.findSimilar(tenantId, queryEmbedding, DEFAULT_TOP_K);

        var relevant = similar.stream()
                .filter(c -> c.similarity() >= SIMILARITY_THRESHOLD)
                .toList();

        if (relevant.isEmpty()) {
            log.debug("No relevant context found for tenant={} (threshold={})", tenantId, SIMILARITY_THRESHOLD);
            return null;
        }

        log.debug("RAG: {} relevant chunk(s) found for tenant={}", relevant.size(), tenantId);
        return buildContextBlock(relevant);
    }

    @Transactional
    public void deleteDocument(String documentName, UUID tenantId) {
        chunkRepository.deleteAllByTenantIdAndDocumentName(tenantId, documentName);
        log.info("Deleted document '{}' for tenant={}", documentName, tenantId);
    }

    @Transactional(readOnly = true)
    public List<String> listDocuments(UUID tenantId) {
        return chunkRepository.findDistinctDocumentNamesByTenantId(tenantId);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private String buildContextBlock(List<SimilarChunk> chunks) {
        var sb = new StringBuilder();
        sb.append("--- RELEVANT COMPANY KNOWLEDGE ---\n");
        for (var chunk : chunks) {
            sb.append("[Source: ").append(chunk.documentName()).append("]\n");
            sb.append(chunk.content()).append("\n\n");
        }
        sb.append("--- END KNOWLEDGE ---");
        return sb.toString();
    }

    private String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        try {
            if (filename.endsWith(".pdf")) {
                return extractPdf(file);
            }
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file '" + filename + "': " + e.getMessage(), e);
        }
    }

    private String extractPdf(MultipartFile file) throws IOException {
        // PDDocument.load() was removed in PDFBox 3.0 — use Loader.loadPDF() instead
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        String name = file.getOriginalFilename();
        // toLowerCase so .PDF and .TXT are accepted too
        String nameLower = name != null ? name.toLowerCase() : "";
        if (SUPPORTED_TYPES.stream().noneMatch(nameLower::endsWith)) {
            throw new IllegalArgumentException("Unsupported file type. Accepted: " + SUPPORTED_TYPES);
        }
    }
}
