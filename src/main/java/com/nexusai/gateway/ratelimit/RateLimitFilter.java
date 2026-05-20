package com.nexusai.gateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusai.gateway.api.ErrorResponse;
import com.nexusai.gateway.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(3)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        var tenantId = TenantContext.getTenant();
        if (tenantId == null || !request.getRequestURI().startsWith("/api/v1/ai/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!rateLimitService.tryConsume(tenantId)) {
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", "60");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            var error = ErrorResponse.of(
                    429,
                    "Too Many Requests",
                    "Rate limit exceeded for your plan. Please wait before retrying.",
                    request.getRequestURI()
            );
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(rateLimitService.availableTokens(tenantId)));
        filterChain.doFilter(request, response);
    }
}
