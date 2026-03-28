package com.housingplatform.identity.service;

import com.housingplatform.identity.dto.AuthResponse;
import com.housingplatform.identity.dto.ForgotPasswordRequest;
import com.housingplatform.identity.dto.LoginRequest;
import com.housingplatform.identity.dto.RegistrationRequest;
import com.housingplatform.identity.dto.ResetPasswordRequest;

public interface AuthenticationService {

  AuthResponse login(LoginRequest request);

  AuthResponse refreshToken(String refreshToken);

  AuthResponse register(RegistrationRequest request);

  /**
   * Invalidates the given access token by adding it to the in-memory blacklist for the remainder of
   * its natural lifetime. Any subsequent request bearing this token will be rejected with 401.
   *
   * @param accessToken the raw JWT access token to invalidate (may be null if none was provided)
   */
  void logout(String accessToken);

  /**
   * Initiates password reset: creates a token, stores it, and sends an email with the reset link.
   * Does not reveal whether the email exists (always returns success for valid email format).
   */
  void requestPasswordReset(ForgotPasswordRequest request);

  /** Resets password using the token from the email link. Invalidates the token after use. */
  void resetPassword(ResetPasswordRequest request);

  void markPhoneAsVerified(String phoneNumber);

  void requestOtpLogin(String phoneNumber);

  AuthResponse confirmOtpLogin(String phoneNumber, String code);
}
