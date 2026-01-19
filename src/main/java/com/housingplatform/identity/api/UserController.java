package com.housingplatform.identity.api;

import com.housingplatform.identity.dto.UserResponse;
import com.housingplatform.identity.dto.UserUpdateRequest;
import com.housingplatform.identity.service.UserService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import com.housingplatform.shared.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class UserController {
    
    private final UserService userService;
    
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieve user information by ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }
    
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Retrieve current authenticated user information")
    public ResponseEntity<UserResponse> getCurrentUser() {
        UserResponse user = userService.getCurrentUserFromContext();
        return ResponseEntity.ok(user);
    }
    
    @PutMapping("/me")
    @Operation(summary = "Update current user", description = "Update current authenticated user information")
    public ResponseEntity<UserResponse> updateCurrentUser(@Valid @RequestBody UserUpdateRequest userRequest) {
        UUID userId = UserContext.getCurrentUserId();
        UserResponse updated = userService.updateUser(userId, userRequest);
        return ResponseEntity.ok(updated);
    }
    
    @PutMapping("/{id}")
    @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
    @AuthActionScope("users.update")
    @Operation(summary = "Update user", description = "Update user information (admin only)")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest userRequest) {
        UserResponse updated = userService.updateUser(id, userRequest);
        return ResponseEntity.ok(updated);
    }
}
