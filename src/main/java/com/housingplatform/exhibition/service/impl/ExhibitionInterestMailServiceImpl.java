package com.housingplatform.exhibition.service.impl;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.service.ExhibitionInterestMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sent after an interest registration is committed. Runs off the request thread so a slow or
 * failing mail server never delays or fails the registration itself; failures are logged. With no
 * mail username configured it only logs, which is what local and test runs want.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitionInterestMailServiceImpl implements ExhibitionInterestMailService {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username:}")
  private String fromEmail;

  /** Where the team is told about each registration; empty disables the notice. */
  @Value("${app.exhibition.notify-email:}")
  private String notifyEmail;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Async
  @Override
  public void sendInterestEmails(ExhibitionInterest interest) {
    if (fromEmail == null || fromEmail.isBlank()) {
      log.warn(
          "Mail not configured (spring.mail.username empty). Interest registration {} ({}) not acknowledged by email.",
          interest.getEmail(),
          interest.getInterestType());
      return;
    }
    sendReceipt(interest);
    sendTeamNotice(interest);
  }

  private void sendReceipt(ExhibitionInterest interest) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromEmail);
      message.setTo(interest.getEmail());
      message.setSubject("We received your interest - Ethio Build Connect Expo 2027");
      message.setText(
          "Thank you for registering your interest in the Ethio Build Connect Expo"
              + " (9-13 January 2027, Addis Convention Center).\n\n"
              + summary(interest)
              + "\nOur team replies to every registration within 2 business days."
              + " Until then you can browse the exhibition, sponsorship packages and listings at "
              + frontendBaseUrl.replaceAll("/$", "")
              + "\n\nEthio Build Connect\n"
              + "Addis Ababa, Ethiopia");
      mailSender.send(message);
      log.info("Interest receipt sent to {}", interest.getEmail());
    } catch (Exception e) {
      log.error("Failed to send interest receipt to {}: {}", interest.getEmail(), e.getMessage());
    }
  }

  private void sendTeamNotice(ExhibitionInterest interest) {
    if (notifyEmail == null || notifyEmail.isBlank()) {
      return;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromEmail);
      message.setTo(notifyEmail.split("\\s*,\\s*"));
      message.setReplyTo(interest.getEmail());
      message.setSubject(
          "New expo interest: " + label(interest.getInterestType()) + " - " + interest.getEmail());
      message.setText(
          summary(interest)
              + "\nReview and follow up in the admin portal: "
              + frontendBaseUrl.replaceAll("/$", "")
              + "/admin/exhibition-interests");
      mailSender.send(message);
      log.info("Interest notice sent to {}", notifyEmail);
    } catch (Exception e) {
      log.error("Failed to send interest notice to {}: {}", notifyEmail, e.getMessage());
    }
  }

  private static String summary(ExhibitionInterest interest) {
    StringBuilder sb = new StringBuilder();
    sb.append("Interest: ").append(label(interest.getInterestType())).append('\n');
    sb.append("Email: ").append(interest.getEmail()).append('\n');
    if (notBlank(interest.getPhoneNumber())) {
      sb.append("Phone: ").append(interest.getPhoneNumber()).append('\n');
    }
    if (notBlank(interest.getCompany())) {
      sb.append("Company: ").append(interest.getCompany()).append('\n');
    }
    if (interest.getSponsorship() != null && notBlank(interest.getSponsorship().getName())) {
      sb.append("Package: ").append(interest.getSponsorship().getName()).append('\n');
    }
    if (notBlank(interest.getMessage())) {
      sb.append("Message: ").append(interest.getMessage().trim()).append('\n');
    }
    return sb.toString();
  }

  private static String label(String interestType) {
    if (interestType == null) return "Visitor";
    return switch (interestType) {
      case "exhibitor" -> "Exhibitor";
      case "partner" -> "Sponsor / partner";
      default -> "Visitor";
    };
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
