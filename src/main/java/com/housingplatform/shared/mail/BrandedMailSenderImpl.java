package com.housingplatform.shared.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrandedMailSenderImpl implements BrandedMailSender {

  private final JavaMailSender mailSender;

  @Value("${app.mail.from:${spring.mail.username:}}")
  private String fromEmail;

  @Value("${app.mail.from-name:Ethio Build Connect}")
  private String fromName;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Value("${app.mail.logo-url:}")
  private String logoUrl;

  @Override
  public boolean configured() {
    return fromEmail != null && !fromEmail.isBlank();
  }

  @Override
  public boolean send(BrandedMail mail) {
    if (!configured()) {
      log.warn(
          "Mail not configured (no sender address). Not sent: '{}' to {}",
          mail.getSubject(),
          mail.getTo());
      return false;
    }
    String site = frontendBaseUrl.replaceAll("/$", "");
    String logo =
        (logoUrl == null || logoUrl.isBlank())
            ? site + "/images/branding/ethio-build-connect-wordmark.png"
            : logoUrl;
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));
      helper.setTo(mail.getTo().toArray(new String[0]));
      if (mail.getReplyTo() != null) helper.setReplyTo(mail.getReplyTo());
      helper.setSubject(mail.getSubject());
      helper.setText(mail.toPlainText(site), mail.toHtml(site, logo));
      if (mail.getInlineImagePng() != null) {
        helper.addInline(
            mail.getInlineImageCid(),
            new org.springframework.core.io.ByteArrayResource(mail.getInlineImagePng()),
            "image/png");
      }
      mailSender.send(message);
      log.info("Mail '{}' sent to {}", mail.getSubject(), mail.getTo());
      return true;
    } catch (Exception e) {
      log.error("Failed to send '{}' to {}: {}", mail.getSubject(), mail.getTo(), e.getMessage());
      return false;
    }
  }
}
