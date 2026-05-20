package com.nexusai.gateway.auth.dto;

import com.nexusai.gateway.auth.model.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String role,
        UUID tenantId,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getTenantId(),
                user.getCreatedAt()
        );
    }
}
