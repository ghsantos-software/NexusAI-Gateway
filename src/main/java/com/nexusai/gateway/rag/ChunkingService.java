package com.nexusai.gateway.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw document text into overlapping chunks suitable for embedding.
 *
 * Strategy:
 * 1. Split by paragraphs (blank lines)
 * 2. If a paragraph is too long, split by sentences
 * 3. Add a small overlap between consecutive chunks to preserve context
 */
@Service
public class ChunkingService {

    static final int MAX_CHUNK_CHARS = 500;
    static final int OVERLAP_CHARS = 100;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> raw = splitIntoParagraphs(text);
        return addOverlap(raw);
    }

    private List<String> splitIntoParagraphs(String text) {
        List<String> chunks = new ArrayList<>();

        for (String paragraph : text.split("\n\n+")) {
            String trimmed = paragraph.strip();
            if (trimmed.isBlank()) continue;

            if (trimmed.length() <= MAX_CHUNK_CHARS) {
                chunks.add(trimmed);
            } else {
                chunks.addAll(splitBySentences(trimmed));
            }
        }

        return chunks;
    }

    private List<String> splitBySentences(String text) {
        List<String> result = new ArrayList<>();
        // Split on sentence-ending punctuation followed by whitespace
        String[] sentences = text.split("(?<=[.!?])\\s+");

        var current = new StringBuilder();
        for (String sentence : sentences) {
            // If adding this sentence would exceed the limit, flush the buffer first
            if (current.length() > 0 && current.length() + sentence.length() + 1 > MAX_CHUNK_CHARS) {
                result.add(current.toString().strip());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(" ");
            current.append(sentence);
        }
        if (!current.isEmpty()) {
            result.add(current.toString().strip());
        }
        return result;
    }

    /**
     * Prepends the tail of the previous chunk to each chunk (except the first).
     * This helps the embedding model understand cross-boundary context.
     */
    private List<String> addOverlap(List<String> chunks) {
        if (chunks.size() <= 1) return chunks;

        List<String> overlapped = new ArrayList<>();
        overlapped.add(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String tail = prev.length() > OVERLAP_CHARS
                    ? prev.substring(prev.length() - OVERLAP_CHARS)
                    : prev;
            overlapped.add(tail + " " + chunks.get(i));
        }

        return overlapped;
    }
}
