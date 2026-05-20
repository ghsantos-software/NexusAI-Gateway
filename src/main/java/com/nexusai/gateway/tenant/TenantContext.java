package com.nexusai.gateway.tenant;

import java.util.UUID;

/**
 * Holds the current tenant ID for the duration of an HTTP request.
 * Populated by JwtAuthFilter after JWT validation.
 * Must be cleared after the request to avoid thread pool leaks.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenant(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
