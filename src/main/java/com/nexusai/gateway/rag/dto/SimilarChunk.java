package com.nexusai.gateway.rag.dto;

public record SimilarChunk(
        String content,
        String documentName,
        double similarity   // 0.0 = unrelated, 1.0 = identical
) {}
