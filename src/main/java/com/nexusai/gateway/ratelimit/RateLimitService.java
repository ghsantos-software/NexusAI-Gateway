package com.nexusai.gateway.ratelimit;

import com.nexusai.gateway.tenant.Tenant.Plan;
import com.nexusai.gateway.tenant.TenantService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final TenantService tenantService;

    // One bucket per tenant, kept in memory.
    // These reset on restart, which is fine for our use case.
    // TODO: if we ever run multiple instances, move this to Redis
    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(UUID tenantId) {
        Bucket bucket = buckets.computeIfAbsent(tenantId, this::createBucket);
        boolean consumed = bucket.tryConsume(1);
        if (!consumed) {
            log.warn("Rate limit exceeded for tenant={}", tenantId);
        }
        return consumed;
    }

    public long availableTokens(UUID tenantId) {
        return buckets.computeIfAbsent(tenantId, this::createBucket).getAvailableTokens();
    }

    private Bucket createBucket(UUID tenantId) {
        var tenant = tenantService.findById(tenantId);
        int rpm = requestsPerMinute(tenant.getPlan());
        log.debug("Creating rate limit bucket: tenant={}, plan={}, rpm={}", tenantId, tenant.getPlan(), rpm);

        // Bucket4j 8.x builder API
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rpm)
                        .refillGreedy(rpm, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private int requestsPerMinute(Plan plan) {
        return switch (plan) {
            case FREE       -> 20;
            case PRO        -> 100;
            case ENTERPRISE -> 500;
        };
    }
}
