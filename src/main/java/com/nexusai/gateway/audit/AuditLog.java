package com.nexusai.gateway.audit;

import com.nexusai.gateway.shared.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends TenantAwareEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String promptHash;

    @Column
    private Integer tokensInput;

    @Column
    private Integer tokensOutput;

    @Column(length = 30)
    private String llmProvider;

    @Column
    private Integer latencyMs;

    @Column(nullable = false)
    private boolean dlpTriggered = false;

    @Column(nullable = false)
    private boolean ragUsed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUCCESS;

    public enum Status {
        SUCCESS, ERROR, BLOCKED
    }
}
