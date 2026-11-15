package com.housingplatform.enquiry.service.impl;

import com.housingplatform.enquiry.domain.ListingEnquiry;
import com.housingplatform.enquiry.service.ListingEnquiryMailService;
import com.housingplatform.identity.domain.Organization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sent after an enquiry is committed, off the request thread: the enquirer gets a receipt, the
 * listing's company gets the enquiry (reply-to the enquirer) and the team gets a copy. Failures are
 * logged, never surfaced to the visitor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListingEnquiryMailServiceImpl implements ListingEnquiryMailService {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username:}")
  private String fromEmail;

  /** Team copy of every enquiry (same inbox as exhibition interest); empty disables it. */
  @Value("${app.exhibition.notify-email:}")
  private String notifyEmail;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Async
  @Override
  public void sendEnquiryEmails(ListingEnquiry enquiry) {
    if (fromEmail == null || fromEmail.isBlank()) {
      log.warn(
          "Mail not configured (spring.mail.username empty). Enquiry from {} about '{}' not emailed.",
          enquiry.getEmail(),
          enquiry.getSubjectTitle());
      return;
    }
    sendReceipt(enquiry);
    sendCompanyNotice(enquiry);
    sendTeamNotice(enquiry);
  }

  private void sendReceipt(ListingEnquiry enquiry) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromEmail);
      message.setTo(enquiry.getEmail());
      message.setSubject("We received your enquiry - " + subject(enquiry));
      message.setText(
          "Hello "
              + enquiry.getName()
              + ",\n\nThank you for your enquiry on Ethio Build Connect. "
              + companyName(enquiry)
              + " has been sent your message and will reply to you directly.\n\n"
              + summary(enquiry)
              + "\nIf you do not hear back within 2 business days, reply to this email and our"
              + " team will follow up for you.\n\nEthio Build Connect\n"
              + base()
              + "\nAddis Ababa, Ethiopia");
      mailSender.send(message);
      log.info("Enquiry receipt sent to {}", enquiry.getEmail());
    } catch (Exception e) {
      log.error("Failed to send enquiry receipt to {}: {}", enquiry.getEmail(), e.getMessage());
    }
  }

  private void sendCompanyNotice(ListingEnquiry enquiry) {
    String to = companyEmail(enquiry);
    if (to == null) {
      log.info("Enquiry {} has no company email; only the team copy is sent.", enquiry.getId());
      return;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromEmail);
      message.setTo(to);
      message.setReplyTo(enquiry.getEmail());
      message.setSubject("New enquiry: " + subject(enquiry) + " - " + enquiry.getName());
      message.setText(
          "A visitor on Ethio Build Connect asked about "
              + subject(enquiry)
              + ".\n\n"
              + summary(enquiry)
              + "\nReply to this email to answer them directly.\n\nEthio Build Connect\n"
              + base());
      mailSender.send(message);
      log.info("Enquiry notice sent to company {}", to);
    } catch (Exception e) {
      log.error("Failed to send enquiry notice to {}: {}", to, e.getMessage());
    }
  }

  private void sendTeamNotice(ListingEnquiry enquiry) {
    if (notifyEmail == null || notifyEmail.isBlank()) {
      return;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromEmail);
      message.setTo(notifyEmail.split("\\s*,\\s*"));
      message.setReplyTo(enquiry.getEmail());
      message.setSubject("Listing enquiry: " + subject(enquiry) + " - " + enquiry.getEmail());
      message.setText(
          summary(enquiry)
              + "Company: "
              + companyName(enquiry)
              + (companyEmail(enquiry) != null
                  ? " (" + companyEmail(enquiry) + ")"
                  : " (no email on file - please forward)")
              + "\n\nTrack and close it in the admin portal: "
              + base()
              + "/admin/enquiries");
      mailSender.send(message);
      log.info("Enquiry copy sent to {}", notifyEmail);
    } catch (Exception e) {
      log.error("Failed to send enquiry copy to {}: {}", notifyEmail, e.getMessage());
    }
  }

  private String base() {
    return frontendBaseUrl.replaceAll("/$", "");
  }

  private static String subject(ListingEnquiry e) {
    if (notBlank(e.getSubjectTitle())) return e.getSubjectTitle();
    return e.getSubjectType() == ListingEnquiry.SubjectType.PROPERTY ? "a property" : "a company";
  }

  private static String companyName(ListingEnquiry e) {
    Organization o = e.getOrganization();
    return o != null && notBlank(o.getName()) ? o.getName() : "The lister";
  }

  private static String companyEmail(ListingEnquiry e) {
    Organization o = e.getOrganization();
    if (o == null || o.getContact() == null) return null;
    String email = o.getContact().getEmail();
    return notBlank(email) ? email.trim() : null;
  }

  private String summary(ListingEnquiry e) {
    StringBuilder sb = new StringBuilder();
    sb.append("About: ").append(subject(e)).append('\n');
    if (notBlank(e.getPageUrl())) {
      sb.append("Page: ").append(e.getPageUrl()).append('\n');
    } else if (e.getProperty() != null) {
      sb.append("Page: ")
          .append(base())
          .append("/properties/")
          .append(e.getProperty().getId())
          .append('\n');
    }
    sb.append("Name: ").append(e.getName()).append('\n');
    sb.append("Email: ").append(e.getEmail()).append('\n');
    if (notBlank(e.getPhoneNumber())) {
      sb.append("Phone: ").append(e.getPhoneNumber()).append('\n');
    }
    sb.append("Message:\n").append(e.getMessage().trim()).append('\n');
    return sb.toString();
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
