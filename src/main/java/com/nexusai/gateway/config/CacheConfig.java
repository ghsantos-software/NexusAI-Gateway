package com.nexusai.gateway.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexusai.gateway.ai.config.AiProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TENANT_CACHE = "tenants";
    public static final String AI_RESPONSE_CACHE = "aiResponses";

    // Wire the TTL from application properties instead of hardcoding it
    @Bean
    public CacheManager cacheManager(AiProperties aiProperties) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(aiProperties.cacheTtlMinutes(), TimeUnit.MINUTES)
                .recordStats());
        return manager;
    }
}
