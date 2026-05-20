package com.nexusai.gateway.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    @Column(nullable = false)
    private int monthlyTokenLimit = 100_000;

    @Column(nullable = false)
    private int tokensUsedThisMonth = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LlmProvider llmProvider = LlmProvider.OPENAI;

    @Column(columnDefinition = "TEXT")
    private String llmApiKeyEncrypted;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Tenant(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public boolean hasTokensAvailable() {
        return tokensUsedThisMonth < monthlyTokenLimit;
    }

    public void addTokensUsed(int tokens) {
        this.tokensUsedThisMonth += tokens;
    }

    public enum Plan {
        FREE, PRO, ENTERPRISE
    }

    public enum LlmProvider {
        OPENAI, ANTHROPIC
    }
}
