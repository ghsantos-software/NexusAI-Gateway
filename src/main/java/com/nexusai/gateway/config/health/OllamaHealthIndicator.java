package com.nexusai.gateway.config.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@Component("ollama")
@ConditionalOnProperty(name = "nexusai.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;
    private final String ollamaUrl;

    public OllamaHealthIndicator(
            RestTemplateBuilder builder,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaUrl) {

        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(3))
                .build();
        this.ollamaUrl = ollamaUrl;
    }

    @Override
    public Health health() {
        try {
            restTemplate.getForObject(ollamaUrl + "/api/tags", String.class);
            return Health.up()
                    .withDetail("url", ollamaUrl)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("url", ollamaUrl)
                    .withDetail("error", e.getMessage())
                    .withDetail("hint", "Run: docker compose up -d ollama")
                    .build();
        }
    }
}
