package com.housingplatform.identity.service;

public interface PasswordResetEmailService {

  /**
   * Sends a password reset email with a link containing the token. If mail is not configured or
   * sending fails, logs the link for development and does not throw.
   */
  void sendPasswordResetEmail(String toEmail, String resetToken);
}
