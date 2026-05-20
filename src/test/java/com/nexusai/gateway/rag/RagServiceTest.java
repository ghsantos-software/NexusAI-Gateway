package com.nexusai.gateway.rag;

import com.nexusai.gateway.rag.dto.SimilarChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock private DocumentChunkRepository chunkRepository;
    @Mock private VectorStore vectorStore;
    @Mock private EmbeddingModel embeddingModel;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(chunkRepository, vectorStore, new ChunkingService(), embeddingModel);
    }

    // ─── Upload ──────────────────────────────────────────────────────────────

    @Test
    void processDocument_validTxtFile_savesChunksAndEmbeddings() {
        var tenantId = UUID.randomUUID();
        var file = new MockMultipartFile("file", "manual.txt", "text/plain",
                "Chapter one.\n\nChapter two has more content here.".getBytes());

        var savedChunk = new DocumentChunk("manual.txt", 0, "Chapter one.");
        savedChunk.setTenantId(tenantId);

        when(chunkRepository.save(any())).thenReturn(savedChunk);
        when(embeddingModel.embed(anyString())).thenReturn(new float[1536]);

        var result = ragService.processDocument(file, tenantId);

        assertThat(result.documentName()).isEqualTo("manual.txt");
        assertThat(result.totalChunks()).isGreaterThan(0);
        verify(chunkRepository, atLeastOnce()).save(any());
        verify(vectorStore, atLeastOnce()).saveEmbedding(any(), any());
    }

    @Test
    void processDocument_emptyFile_throwsIllegalArgument() {
        var file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> ragService.processDocument(file, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void processDocument_unsupportedType_throwsIllegalArgument() {
        var file = new MockMultipartFile("file", "data.csv", "text/csv", "a,b,c".getBytes());
        assertThatThrownBy(() -> ragService.processDocument(file, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    // ─── Semantic Search ─────────────────────────────────────────────────────

    @Test
    void findRelevantContext_noDocumentsForTenant_returnsNull() {
        when(chunkRepository.existsByTenantId(any())).thenReturn(false);

        String context = ragService.findRelevantContext("any query", UUID.randomUUID());

        assertThat(context).isNull();
        verifyNoInteractions(embeddingModel, vectorStore);
    }

    @Test
    void findRelevantContext_relevantChunksFound_returnsFormattedContext() {
        var tenantId = UUID.randomUUID();
        float[] dummyEmbedding = new float[1536];

        when(chunkRepository.existsByTenantId(tenantId)).thenReturn(true);
        when(embeddingModel.embed(anyString())).thenReturn(dummyEmbedding);
        when(vectorStore.findSimilar(eq(tenantId), eq(dummyEmbedding), anyInt()))
                .thenReturn(List.of(
                        new SimilarChunk("Our refund policy is 30 days.", "policy.txt", 0.85),
                        new SimilarChunk("Requests must be submitted via email.", "policy.txt", 0.72)
                ));

        String context = ragService.findRelevantContext("How do I request a refund?", tenantId);

        assertThat(context).isNotNull();
        assertThat(context).contains("RELEVANT COMPANY KNOWLEDGE");
        assertThat(context).contains("Our refund policy is 30 days.");
        assertThat(context).contains("[Source: policy.txt]");
    }

    @Test
    void findRelevantContext_allChunksBelowThreshold_returnsNull() {
        var tenantId = UUID.randomUUID();
        float[] dummyEmbedding = new float[1536];

        when(chunkRepository.existsByTenantId(tenantId)).thenReturn(true);
        when(embeddingModel.embed(anyString())).thenReturn(dummyEmbedding);
        when(vectorStore.findSimilar(eq(tenantId), any(), anyInt()))
                .thenReturn(List.of(
                        new SimilarChunk("Unrelated content.", "other.txt", 0.10)
                ));

        String context = ragService.findRelevantContext("specific question", tenantId);

        assertThat(context).isNull();
    }

    @Test
    void findRelevantContext_embeddingFails_returnsNullGracefully() {
        var tenantId = UUID.randomUUID();
        when(chunkRepository.existsByTenantId(tenantId)).thenReturn(true);
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("API timeout"));

        String context = ragService.findRelevantContext("any query", tenantId);
        assertThat(context).isNull();
    }
}
