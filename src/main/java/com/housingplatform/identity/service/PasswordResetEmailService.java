package com.housingplatform.identity.service;

public interface PasswordResetEmailService {

  /**
   * Sends a password reset email with a link containing the token. If mail is not configured or
   * sending fails, logs the link for development and does not throw.
   */
  void sendPasswordResetEmail(String toEmail, String resetToken);

  /**
   * Sends a welcome email to a newly provisioned company account with a link to set their own
   * password. The link uses the same reset-token mechanism as {@link #sendPasswordResetEmail}, so
   * the operator never has to hand over a password out of band. Fails soft, like the reset email.
   */
  void sendAccountWelcomeEmail(String toEmail, String setPasswordToken, String organizationName);
}
