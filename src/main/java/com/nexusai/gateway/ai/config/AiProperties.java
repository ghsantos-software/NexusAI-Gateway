package com.nexusai.gateway.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexusai.ai")
public record AiProperties(
        String provider,
        boolean cacheEnabled,
        int cacheTtlMinutes
) {}
