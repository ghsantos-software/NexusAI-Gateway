package com.nexusai.gateway.dlp.model;

import com.nexusai.gateway.shared.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dlp_rules")
@Getter
@Setter
@NoArgsConstructor
public class DlpRule extends TenantAwareEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String patternType;

    @Column(columnDefinition = "TEXT")
    private String regexPattern;

    @Column(nullable = false, length = 20)
    private String action = "REDACT";

    @Column(nullable = false)
    private boolean active = true;
}
