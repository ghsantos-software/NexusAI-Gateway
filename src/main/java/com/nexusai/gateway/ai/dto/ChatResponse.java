package com.nexusai.gateway.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        String content,
        String provider,
        Integer tokensInput,
        Integer tokensOutput,
        long latencyMs,
        boolean dlpApplied,
        boolean ragUsed
) {}
