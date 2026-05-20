package com.nexusai.gateway.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingService();
    }

    @Test
    void chunk_shortText_returnsSingleChunk() {
        String text = "This is a short paragraph.";
        List<String> chunks = chunkingService.chunk(text);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(text);
    }

    @Test
    void chunk_multipleParagraphs_returnsOneChunkPerParagraph() {
        String text = "First paragraph content.\n\nSecond paragraph content.\n\nThird paragraph content.";
        List<String> chunks = chunkingService.chunk(text);
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).isEqualTo("First paragraph content.");
        assertThat(chunks.get(1)).contains("Second paragraph content.");
    }

    @Test
    void chunk_longParagraph_splitsBySentences() {
        String longParagraph = "Sentence one is here. ".repeat(30);
        List<String> chunks = chunkingService.chunk(longParagraph);
        assertThat(chunks.size()).isGreaterThan(1);
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThan(ChunkingService.MAX_CHUNK_CHARS + ChunkingService.OVERLAP_CHARS + 50);
        }
    }

    @Test
    void chunk_nullInput_returnsEmptyList() {
        assertThat(chunkingService.chunk(null)).isEmpty();
    }

    @Test
    void chunk_blankInput_returnsEmptyList() {
        assertThat(chunkingService.chunk("   \n\n  ")).isEmpty();
    }

    @Test
    void chunk_singleParagraph_noOverlapAdded() {
        String text = "Just one paragraph.";
        var chunks = chunkingService.chunk(text);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(text);
    }

    @Test
    void chunk_overlapContentsFromPreviousChunk() {
        String p1 = "First paragraph with some content here.";
        String p2 = "Second paragraph with different content.";
        String text = p1 + "\n\n" + p2;

        var chunks = chunkingService.chunk(text);
        assertThat(chunks).hasSize(2);

        String secondChunk = chunks.get(1);
        assertThat(secondChunk).contains("Second paragraph");
        assertThat(secondChunk).startsWith(p1.substring(Math.max(0, p1.length() - ChunkingService.OVERLAP_CHARS)));
    }
}
