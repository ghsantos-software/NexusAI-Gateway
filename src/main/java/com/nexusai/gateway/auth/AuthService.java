package com.nexusai.gateway.auth;

import com.nexusai.gateway.auth.dto.LoginRequest;
import com.nexusai.gateway.auth.dto.RegisterRequest;
import com.nexusai.gateway.auth.dto.TokenResponse;
import com.nexusai.gateway.auth.model.User;
import com.nexusai.gateway.config.JwtProperties;
import com.nexusai.gateway.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantService tenantService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }

        // New registration creates a fresh tenant (self-service flow)
        var tenant = tenantService.create(request.companyName());

        var user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                User.Role.ADMIN
        );
        user.setTenantId(tenant.getId());
        userRepository.save(user);

        log.info("New tenant registered: slug={}, adminEmail={}", tenant.getSlug(), user.getEmail());

        String token = jwtService.generateToken(user);
        return TokenResponse.of(token, jwtProperties.expirationMs());
    }

    public TokenResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        return TokenResponse.of(token, jwtProperties.expirationMs());
    }
}
