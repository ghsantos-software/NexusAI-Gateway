package com.nexusai.gateway.rag.dto;

public record UploadResponse(
        String documentName,
        int totalChunks,
        int embeddedChunks
) {}
