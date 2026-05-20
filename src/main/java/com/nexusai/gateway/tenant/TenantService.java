package com.nexusai.gateway.tenant;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

import static com.nexusai.gateway.config.CacheConfig.TENANT_CACHE;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    @Transactional
    public Tenant create(String companyName) {
        String slug = generateSlug(companyName);
        var tenant = new Tenant(companyName, slug);
        return tenantRepository.save(tenant);
    }

    @Cacheable(value = TENANT_CACHE, key = "#tenantId")
    public Tenant findById(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
    }

    @Transactional
    @CacheEvict(value = TENANT_CACHE, key = "#tenantId")
    public void addTokensUsed(UUID tenantId, int tokens) {
        var tenant = findById(tenantId);
        tenant.addTokensUsed(tokens);
        tenantRepository.save(tenant);
    }

    // TODO: add a scheduled job to reset tokensUsedThisMonth at the start of each billing cycle

    private String generateSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        String slug = base;
        int suffix = 1;
        while (tenantRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
