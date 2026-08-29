package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.service.PasswordResetEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailServiceImpl implements PasswordResetEmailService {

  private final JavaMailSender mailSender;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Value("${spring.mail.username:}")
  private String fromEmail;

  @Override
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
      message.setSubject("Reset your password - Ethio Build Connect");
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

  @Override
  public void sendAccountWelcomeEmail(
      String toEmail, String setPasswordToken, String organizationName) {
    String setPasswordUrl =
        frontendBaseUrl.replaceAll("/$", "") + "/reset-password?token=" + setPasswordToken;
    String org =
        (organizationName == null || organizationName.isBlank())
            ? "your organization"
            : organizationName;

    if (fromEmail == null || fromEmail.isBlank()) {
      log.warn(
          "Mail not configured (spring.mail.username empty). Account set-password link for {}: {}",
          toEmail,
          setPasswordUrl);
      return;
    }

    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromEmail);
      message.setTo(toEmail);
      message.setSubject("Your Ethio Build Connect account");
      message.setText(
          "An account has been created for you to manage "
              + org
              + " on Ethio Build Connect.\n\n"
              + "Set your password using the link below (valid for a limited time):\n\n"
              + setPasswordUrl
              + "\n\nAfter setting your password, sign in with this email address.");
      mailSender.send(message);
      log.info("Account welcome email sent to {}", toEmail);
    } catch (Exception e) {
      log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
      log.warn("Account set-password link for {}: {}", toEmail, setPasswordUrl);
    }
  }
}
