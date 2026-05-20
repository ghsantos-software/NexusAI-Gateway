package com.nexusai.gateway.ai;

import com.nexusai.gateway.ai.config.AiProperties;
import com.nexusai.gateway.ai.dto.ChatRequest;
import com.nexusai.gateway.ai.dto.ChatResponse;
import com.nexusai.gateway.audit.AuditLog;
import com.nexusai.gateway.audit.AuditService;
import com.nexusai.gateway.auth.UserService;
import com.nexusai.gateway.config.CacheConfig;
import com.nexusai.gateway.dlp.DlpService;
import com.nexusai.gateway.rag.RagService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiGatewayService {

    private final ChatClient chatClient;
    private final DlpService dlpService;
    private final RagService ragService;
    private final AuditService auditService;
    private final UserService userService;
    private final AiProperties aiProperties;
    private final CacheManager cacheManager;

    private final Counter requestsTotal;
    private final Counter errorsTotal;
    private final Timer requestDuration;

    public AiGatewayService(
            ChatClient chatClient,
            DlpService dlpService,
            RagService ragService,
            AuditService auditService,
            UserService userService,
            AiProperties aiProperties,
            CacheManager cacheManager,
            MeterRegistry meterRegistry) {

        this.chatClient = chatClient;
        this.dlpService = dlpService;
        this.ragService = ragService;
        this.auditService = auditService;
        this.userService = userService;
        this.aiProperties = aiProperties;
        this.cacheManager = cacheManager;

        this.requestsTotal = Counter.builder("nexusai.requests.total")
                .description("Total AI chat requests")
                .tag("provider", aiProperties.provider())
                .register(meterRegistry);

        this.errorsTotal = Counter.builder("nexusai.errors.total")
                .description("LLM call failures")
                .tag("provider", aiProperties.provider())
                .register(meterRegistry);

        this.requestDuration = Timer.builder("nexusai.request.duration")
                .description("End-to-end request latency in milliseconds")
                .tag("provider", aiProperties.provider())
                .register(meterRegistry);
    }

    private static final String BASE_SYSTEM_PROMPT = """
            You are a corporate AI assistant. Be precise, professional, and concise.
            Some sensitive data in the user's message may have been automatically redacted for compliance
            (shown as [CPF_REDACTED], [EMAIL_REDACTED], [PHONE_REDACTED], [CARD_REDACTED]).
            """;

    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        var user = userService.getCurrentUser();
        requestsTotal.increment();

        var masked = dlpService.mask(request.message());
        if (masked.wasMasked()) {
            log.info("DLP masked {} occurrence(s) for tenant={}", masked.occurrences(), user.getTenantId());
        }

        // Cache is skipped when RAG is active — context changes with document state
        boolean ragRequested = !Boolean.FALSE.equals(request.useRag());
        String cacheKey = buildCacheKey(user.getTenantId().toString(), masked.maskedText());
        var cache = cacheManager.getCache(CacheConfig.AI_RESPONSE_CACHE);
        if (aiProperties.cacheEnabled() && !ragRequested && cache != null) {
            var cached = cache.get(cacheKey, ChatResponse.class);
            if (cached != null) {
                log.debug("Cache hit for tenant={}", user.getTenantId());
                requestDuration.record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                return cached;
            }
        }

        String context = null;
        if (ragRequested) {
            context = ragService.findRelevantContext(masked.maskedText(), user.getTenantId());
            if (context != null) {
                log.info("RAG context injected for tenant={}", user.getTenantId());
            }
        }

        String systemPrompt = buildSystemPrompt(request.systemPrompt(), context);

        Integer tokensIn = null;
        Integer tokensOut = null;
        String content;
        boolean ragUsed = context != null;

        try {
            var aiResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(masked.maskedText())
                    .call()
                    .chatResponse();

            content = aiResponse.getResult().getOutput().getText();

            var usage = aiResponse.getMetadata().getUsage();
            if (usage != null) {
                tokensIn  = usage.getPromptTokens();
                tokensOut = usage.getCompletionTokens();
            }

        } catch (Exception e) {
            long failedLatency = System.currentTimeMillis() - startTime;
            errorsTotal.increment();
            log.error("LLM call failed for tenant={} provider={}: {}",
                    user.getTenantId(), aiProperties.provider(), e.getMessage());

            auditService.log(user.getTenantId(), user.getId(), request.message(),
                    null, null, aiProperties.provider(), failedLatency,
                    masked.wasMasked(), ragUsed, AuditLog.Status.ERROR);

            throw new RuntimeException("AI service is unavailable. Check if Ollama is running or verify your API key.");
        }

        long latencyMs = System.currentTimeMillis() - startTime;
        requestDuration.record(latencyMs, TimeUnit.MILLISECONDS);

        log.info("LLM chat: provider={}, latency={}ms, tokensIn={}, tokensOut={}, dlp={}, rag={}",
                aiProperties.provider(), latencyMs, tokensIn, tokensOut, masked.wasMasked(), ragUsed);

        auditService.log(user.getTenantId(), user.getId(), request.message(),
                tokensIn, tokensOut, aiProperties.provider(), latencyMs,
                masked.wasMasked(), ragUsed, AuditLog.Status.SUCCESS);

        var response = new ChatResponse(
                content, aiProperties.provider(), tokensIn, tokensOut, latencyMs,
                masked.wasMasked(), ragUsed
        );

        if (aiProperties.cacheEnabled() && !ragUsed && cache != null) {
            cache.put(cacheKey, response);
        }

        return response;
    }

    private String buildSystemPrompt(String customPrompt, String ragContext) {
        String base = (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : BASE_SYSTEM_PROMPT;
        if (ragContext == null) return base;
        return base + "\n\n" + ragContext + "\n\nUse the knowledge above to answer when relevant.";
    }

    private String buildCacheKey(String tenantId, String prompt) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var raw = (tenantId + "|" + prompt).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(raw));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
