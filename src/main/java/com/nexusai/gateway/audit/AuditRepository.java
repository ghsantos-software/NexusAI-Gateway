package com.nexusai.gateway.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
