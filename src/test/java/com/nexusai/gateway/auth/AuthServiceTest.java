package com.nexusai.gateway.auth;

import com.nexusai.gateway.auth.dto.LoginRequest;
import com.nexusai.gateway.auth.dto.RegisterRequest;
import com.nexusai.gateway.auth.model.User;
import com.nexusai.gateway.config.JwtProperties;
import com.nexusai.gateway.tenant.Tenant;
import com.nexusai.gateway.tenant.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantService tenantService;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        var props = new JwtProperties("test-secret", 3_600_000L);
        authService = new AuthService(userRepository, tenantService, jwtService, passwordEncoder, props);
    }

    @Test
    void register_newEmail_createsUserAndReturnToken() {
        var request = new RegisterRequest("Acme Corp", "admin@acme.com", "password123");
        var tenant = new Tenant("Acme Corp", "acme-corp");

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(tenantService.create("Acme Corp")).thenReturn(tenant);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("jwt-token-here");

        var response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token-here");
        assertThat(response.type()).isEqualTo("Bearer");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateEmail_throwsIllegalArgument() {
        var request = new RegisterRequest("Acme", "existing@acme.com", "pass");
        when(userRepository.existsByEmail("existing@acme.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void login_correctCredentials_returnsToken() {
        var request = new LoginRequest("admin@acme.com", "password123");
        var user = makeUser("admin@acme.com", "hashed");

        when(userRepository.findByEmail("admin@acme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token-here");

        var response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token-here");
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        var request = new LoginRequest("admin@acme.com", "wrong-pass");
        var user = makeUser("admin@acme.com", "hashed");

        when(userRepository.findByEmail("admin@acme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_userNotFound_throwsBadCredentials() {
        var request = new LoginRequest("nobody@acme.com", "pass");
        when(userRepository.findByEmail("nobody@acme.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_disabledUser_throwsBadCredentials() {
        var request = new LoginRequest("disabled@acme.com", "pass");
        var user = makeUser("disabled@acme.com", "hashed");
        user.setActive(false);

        when(userRepository.findByEmail("disabled@acme.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("disabled");
    }

    private User makeUser(String email, String passwordHash) {
        var user = new User(email, passwordHash, User.Role.ADMIN);
        user.setTenantId(UUID.randomUUID());
        return user;
    }
}
