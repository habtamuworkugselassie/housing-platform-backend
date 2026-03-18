package com.housingplatform.identity.api;

import com.housingplatform.identity.dto.AuthResponse;
import com.housingplatform.identity.dto.ForgotPasswordRequest;
import com.housingplatform.identity.dto.LoginRequest;
import com.housingplatform.identity.dto.RegistrationRequest;
import com.housingplatform.identity.dto.ResetPasswordRequest;
import com.housingplatform.identity.dto.SendOtpRequest;
import com.housingplatform.identity.dto.VerifyOtpRequest;
import com.housingplatform.identity.service.AuthenticationService;
import com.housingplatform.identity.service.VerificationService;
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
  private final VerificationService verificationService;

  @PostMapping("/register")
  @Operation(
      summary = "User registration",
      description =
          "Register a new user account with selected role. Public endpoint - no authentication required.")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegistrationRequest request) {
    AuthResponse response = authenticationService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/login")
  @Operation(
      summary = "User login",
      description = "Authenticate user with username/email/phone and password, returns JWT token")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = authenticationService.login(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Refresh token",
      description =
          "Refresh access token using refresh token. Token can be provided in Authorization header or request body.")
  public ResponseEntity<AuthResponse> refreshToken(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody(required = false) com.housingplatform.identity.dto.RefreshTokenRequest request) {

    String refreshToken = null;

    // Try to get token from request body first
    if (request != null
        && request.getRefreshToken() != null
        && !request.getRefreshToken().trim().isEmpty()) {
      refreshToken = request.getRefreshToken().trim();
    }
    // Fallback to Authorization header
    else if (authorization != null && !authorization.trim().isEmpty()) {
      refreshToken = authorization.replace("Bearer ", "").trim();
    }

    if (refreshToken == null || refreshToken.isEmpty()) {
      throw new com.housingplatform.shared.exception.BusinessException(
          "Refresh token is required. Provide it in the request body as 'refreshToken' or in the Authorization header as 'Bearer <token>'");
    }

    AuthResponse response = authenticationService.refreshToken(refreshToken);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  @Operation(
      summary = "User logout",
      description =
          "Logout user (client should discard tokens). Intentionally unsecured so clients"
              + " can always call it, even with an expired or invalid token.")
  // UNSECURED: logout must be callable regardless of token validity, otherwise clients
  // with an invalid token would be unable to logout (the 401 from the logout call itself
  // would be re-intercepted and create an infinite loop on the frontend).
  public ResponseEntity<Void> logout(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    String accessToken = null;
    if (authorization != null && authorization.startsWith("Bearer ")) {
      accessToken = authorization.substring(7).trim();
    }
    authenticationService.logout(accessToken);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/forgot-password")
  @Operation(
      summary = "Request password reset",
      description =
          "Sends a password reset link to the given email if the account exists. Always returns 200 to avoid email enumeration.")
  public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    authenticationService.requestPasswordReset(request);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/reset-password")
  @Operation(
      summary = "Reset password",
      description = "Sets a new password using the token from the reset email link.")
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    authenticationService.resetPassword(request);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/verify/send")
  @Operation(
      summary = "Send WhatsApp OTP",
      description =
          "Dispatches a 6-digit one-time password to the user's registered WhatsApp number.")
  public ResponseEntity<Void> sendVerificationCode(@Valid @RequestBody SendOtpRequest request) {
    verificationService.sendWhatsAppOtp(request.getPhoneNumber());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/verify/confirm")
  @Operation(
      summary = "Confirm WhatsApp OTP",
      description = "Validates the 6-digit OTP and marks the user's phone as verified.")
  public ResponseEntity<Void> confirmVerificationCode(
      @Valid @RequestBody VerifyOtpRequest request) {
    boolean isValid = verificationService.verifyOtp(request.getPhoneNumber(), request.getCode());
    if (isValid) {
      authenticationService.markPhoneAsVerified(request.getPhoneNumber());
      return ResponseEntity.ok().build();
    } else {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  @PostMapping("/login/otp/send")
  @Operation(
      summary = "Request OTP for Login",
      description =
          "Sends a 6-digit one-time password to the user's registered WhatsApp number for login.")
  public ResponseEntity<Void> requestOtpLogin(@Valid @RequestBody SendOtpRequest request) {
    authenticationService.requestOtpLogin(request.getPhoneNumber());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/login/otp/confirm")
  @Operation(
      summary = "Confirm OTP for Login",
      description = "Validates the 6-digit OTP and logs the user in, returning a JWT token.")
  public ResponseEntity<AuthResponse> confirmOtpLogin(
      @Valid @RequestBody VerifyOtpRequest request) {
    AuthResponse response =
        authenticationService.confirmOtpLogin(request.getPhoneNumber(), request.getCode());
    return ResponseEntity.ok(response);
  }
}
