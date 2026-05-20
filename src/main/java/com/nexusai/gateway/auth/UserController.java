package com.nexusai.gateway.auth;

import com.nexusai.gateway.auth.dto.UserResponse;
import com.nexusai.gateway.shared.ApiResponse;
import com.nexusai.gateway.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the authenticated user's profile")
    public ApiResponse<UserResponse> me() {
        var user = userService.getCurrentUser();
        return ApiResponse.ok(UserResponse.from(user));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users in tenant", description = "Admin only — returns all users in the same tenant")
    public ApiResponse<List<UserResponse>> listUsers() {
        var users = userService.findAllByTenant(TenantContext.getTenant())
                .stream()
                .map(UserResponse::from)
                .toList();
        return ApiResponse.ok(users);
    }
}
