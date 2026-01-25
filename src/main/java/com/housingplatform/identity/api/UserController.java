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
import org.springframework.data.domain.Sort;
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
    
    @GetMapping
    @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
    @Operation(summary = "List users", description = "Retrieve all users with pagination and filtering (admin only)")
    public ResponseEntity<org.springframework.data.domain.Page<UserResponse>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        // Parse sort parameter (format: "field,direction" or "field")
        Sort sortObj = Sort.unsorted();
        if (sort != null && !sort.trim().isEmpty()) {
            String[] sortParts = sort.split(",");
            if (sortParts.length == 2) {
                Sort.Direction direction = sortParts[1].trim().equalsIgnoreCase("asc") 
                    ? Sort.Direction.ASC 
                    : Sort.Direction.DESC;
                sortObj = Sort.by(direction, sortParts[0].trim());
            } else if (sortParts.length == 1) {
                sortObj = Sort.by(Sort.Direction.DESC, sortParts[0].trim());
            }
        }
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, sortObj);
        org.springframework.data.domain.Page<UserResponse> users = userService.getAllUsers(search, role, status, pageable);
        return ResponseEntity.ok(users);
    }
    
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
