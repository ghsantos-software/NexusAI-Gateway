package com.nexusai.gateway.auth.dto;

public record TokenResponse(
        String token,
        String type,
        long expiresIn
) {
    public static TokenResponse of(String token, long expiresInMs) {
        return new TokenResponse(token, "Bearer", expiresInMs / 1000);
    }
}
