package com.nexusai.gateway.auth;

import com.nexusai.gateway.auth.model.User;
import com.nexusai.gateway.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        var props = new JwtProperties(
                "test-secret-key-for-unit-tests-only-must-be-32-bytes-long",
                3_600_000L
        );
        jwtService = new JwtService(props);

        testUser = new User("john@acme.com", "hashed-password", User.Role.ADMIN);
        testUser.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void generateToken_returnsNonEmptyToken() {
        String token = jwtService.generateToken(testUser);
        assertThat(token).isNotBlank();
    }

    @Test
    void extractEmail_returnsCorrectEmail() {
        String token = jwtService.generateToken(testUser);
        assertThat(jwtService.extractEmail(token)).isEqualTo("john@acme.com");
    }

    @Test
    void extractTenantId_returnsCorrectTenantId() {
        String token = jwtService.generateToken(testUser);
        UUID tenantId = jwtService.extractTenantId(token);
        assertThat(tenantId).isEqualTo(testUser.getTenantId());
    }

    @Test
    void isTokenValid_withCorrectUser_returnsTrue() {
        String token = jwtService.generateToken(testUser);
        assertThat(jwtService.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    void isTokenValid_withWrongEmail_returnsFalse() {
        String token = jwtService.generateToken(testUser);

        var otherUser = new User("other@acme.com", "hash", User.Role.USER);
        otherUser.setTenantId(testUser.getTenantId());

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        var expiredProps = new JwtProperties(
                "test-secret-key-for-unit-tests-only-must-be-32-bytes-long",
                -1000L // already expired
        );
        var expiredJwtService = new JwtService(expiredProps);
        String token = expiredJwtService.generateToken(testUser);

        assertThat(jwtService.isTokenValid(token, testUser)).isFalse();
    }

    @Test
    void extractEmail_withTamperedToken_throwsException() {
        assertThatThrownBy(() -> jwtService.extractEmail("this.is.not.a.valid.token"))
                .isInstanceOf(Exception.class);
    }
}
