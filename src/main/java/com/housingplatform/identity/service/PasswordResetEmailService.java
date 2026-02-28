package com.housingplatform.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailService {

  private final JavaMailSender mailSender;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Value("${spring.mail.username:}")
  private String fromEmail;

  /**
   * Sends a password reset email with a link containing the token. If mail is not configured or
   * sending fails, logs the link for development and does not throw.
   */
  public void sendPasswordResetEmail(String toEmail, String resetToken) {
    String resetUrl = frontendBaseUrl.replaceAll("/$", "") + "/reset-password?token=" + resetToken;

    if (fromEmail == null || fromEmail.isBlank()) {
      log.warn(
          "Mail not configured (spring.mail.username empty). Password reset link for {}: {}",
          toEmail,
          resetUrl);
      return;
    }

    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromEmail);
      message.setTo(toEmail);
      message.setSubject("Reset your password - Housing Platform");
      message.setText(
          "You requested a password reset. Click the link below to set a new password (valid for 1 hour):\n\n"
              + resetUrl
              + "\n\nIf you did not request this, please ignore this email.");
      mailSender.send(message);
      log.info("Password reset email sent to {}", toEmail);
    } catch (Exception e) {
      log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
      log.warn("Password reset link for {}: {}", toEmail, resetUrl);
    }
  }
}
