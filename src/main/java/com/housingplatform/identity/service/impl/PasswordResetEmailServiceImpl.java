package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.service.PasswordResetEmailService;
import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailServiceImpl implements PasswordResetEmailService {

  private final BrandedMailSender sender;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Override
  public void sendPasswordResetEmail(String toEmail, String resetToken) {
    String resetUrl = frontendBaseUrl.replaceAll("/$", "") + "/reset-password?token=" + resetToken;
    boolean sent =
        sender.send(
            BrandedMail.create()
                .to(toEmail)
                .subject("Reset your password - Ethio Build Connect")
                .preheader("Use the button to choose a new password. The link works for 1 hour.")
                .title("Reset your password")
                .greeting("Hello,")
                .paragraph(
                    "You asked to reset the password for your Ethio Build Connect account. Use the"
                        + " button below to choose a new one. The link is valid for 1 hour.")
                .cta("Set a new password", resetUrl)
                .closing(
                    "If you did not request this, you can ignore this email; your password stays as it is.")
                .closing(
                    "If the button does not work, copy this link into your browser: " + resetUrl));
    if (!sent) {
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
    boolean sent =
        sender.send(
            BrandedMail.create()
                .to(toEmail)
                .subject("Your Ethio Build Connect account")
                .preheader("Set your password to start managing " + org + ".")
                .title("Welcome to Ethio Build Connect")
                .greeting("Hello,")
                .paragraph(
                    "An account has been created for you to manage "
                        + org
                        + " on Ethio Build Connect. Set your password to get started; the link is"
                        + " valid for a limited time.")
                .detail("Sign-in email", toEmail)
                .detail("Organization", org)
                .cta("Set your password", setPasswordUrl)
                .closing("After setting your password, sign in with this email address.")
                .closing(
                    "If the button does not work, copy this link into your browser: "
                        + setPasswordUrl));
    if (!sent) {
      log.warn("Account set-password link for {}: {}", toEmail, setPasswordUrl);
    }
  }
}
