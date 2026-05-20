package com.nexusai.gateway.audit.dto;

import com.nexusai.gateway.audit.AuditLog;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String promptHash,
        String llmProvider,
        Integer tokensInput,
        Integer tokensOutput,
        Integer latencyMs,
        boolean dlpTriggered,
        boolean ragUsed,
        String status,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getPromptHash(),
                log.getLlmProvider(),
                log.getTokensInput(),
                log.getTokensOutput(),
                log.getLatencyMs(),
                log.isDlpTriggered(),
                log.isRagUsed(),
                log.getStatus().name(),
                log.getCreatedAt()
        );
    }
}
