package com.nexusai.gateway.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository auditRepository;

    @Async("auditExecutor")
    public void log(UUID tenantId, UUID userId, String originalPrompt,
                    Integer tokensIn, Integer tokensOut, String provider,
                    long latencyMs, boolean dlpTriggered, boolean ragUsed,
                    AuditLog.Status status) {
        try {
            var entry = new AuditLog();
            entry.setTenantId(tenantId);
            entry.setUserId(userId);
            entry.setPromptHash(sha256(originalPrompt));
            entry.setTokensInput(tokensIn);
            entry.setTokensOutput(tokensOut);
            entry.setLlmProvider(provider);
            entry.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
            entry.setDlpTriggered(dlpTriggered);
            entry.setRagUsed(ragUsed);
            entry.setStatus(status);
            auditRepository.save(entry);
        } catch (Exception e) {
            // Audit failures must never propagate to the main request thread
            log.error("Failed to persist audit log for tenant={}: {}", tenantId, e.getMessage());
        }
    }

    private String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
