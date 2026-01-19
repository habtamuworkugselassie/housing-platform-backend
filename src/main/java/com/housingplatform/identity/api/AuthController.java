package com.housingplatform.identity.api;

import com.housingplatform.identity.dto.AuthResponse;
import com.housingplatform.identity.dto.LoginRequest;
import com.housingplatform.identity.dto.RegistrationRequest;
import com.housingplatform.identity.service.AuthenticationService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User authentication APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class AuthController {
    
    private final AuthenticationService authenticationService;
    
    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register a new user account with selected role. Public endpoint - no authentication required.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegistrationRequest request) {
        AuthResponse response = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user with username/email/phone and password, returns JWT token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authenticationService.login(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String authorization) {
        String refreshToken = authorization.replace("Bearer ", "").trim();
        AuthResponse response = authenticationService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Logout user (client should discard tokens). Requires authentication.")
    @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED) // Override class-level UNSECURED policy
    public ResponseEntity<Void> logout() {
        // In a stateless JWT system, logout is primarily handled client-side by discarding tokens
        // The backend endpoint provides a way to explicitly log out and can be extended
        // to support token blacklisting (e.g., using Redis) if needed
        authenticationService.logout();
        return ResponseEntity.ok().build();
    }
}
