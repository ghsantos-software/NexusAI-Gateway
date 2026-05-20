package com.nexusai.gateway.audit;

import com.nexusai.gateway.audit.dto.AuditLogResponse;
import com.nexusai.gateway.shared.ApiResponse;
import com.nexusai.gateway.shared.PageResponse;
import com.nexusai.gateway.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "AI request history for compliance and monitoring")
@SecurityRequirement(name = "Bearer Authentication")
public class AuditController {

    private final AuditRepository auditRepository;

    @GetMapping
    @Operation(
            summary = "List audit logs",
            description = "Returns paginated AI request history for the current tenant. " +
                    "Prompts are stored as SHA-256 hashes — raw text is never persisted."
    )
    public ApiResponse<PageResponse<AuditLogResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var tenantId = TenantContext.getTenant();
        var pageable = PageRequest.of(page, Math.min(size, 100));
        var logs = auditRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId, pageable);

        return ApiResponse.ok(PageResponse.from(logs.map(AuditLogResponse::from)));
    }
}
