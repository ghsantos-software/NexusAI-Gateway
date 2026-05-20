package com.nexusai.gateway.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "Message cannot be blank")
        @Size(max = 4000, message = "Message must be at most 4000 characters")
        String message,

        // Optional: tenant can override the default system prompt
        @Size(max = 1000, message = "System prompt must be at most 1000 characters")
        String systemPrompt,

        // null or true = use RAG if documents are available for this tenant
        // false = skip RAG even if documents exist
        Boolean useRag
) {}
