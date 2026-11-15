package com.housingplatform.enquiry.service.impl;

import com.housingplatform.enquiry.domain.ListingEnquiry;
import com.housingplatform.enquiry.service.ListingEnquiryMailService;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

  private final BrandedMailSender sender;

  /** Team copy of every enquiry (same inbox as exhibition interest); empty disables it. */
  @Value("${app.exhibition.notify-email:}")
  private String notifyEmail;

  /** Replies to receipts go here (the team inbox) when the sender is a no-reply address. */
  @Value("${app.mail.reply-to:}")
  private String replyTo;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Async
  @Override
  public void sendEnquiryEmails(ListingEnquiry enquiry) {
    if (!sender.configured()) {
      log.warn(
          "Mail not configured. Enquiry from {} about '{}' not emailed.",
          enquiry.getEmail(),
          enquiry.getSubjectTitle());
      return;
    }
    sender.send(receipt(enquiry));
    String companyTo = companyEmail(enquiry);
    if (companyTo != null) {
      sender.send(companyNotice(enquiry, companyTo));
    } else {
      log.info("Enquiry {} has no company email; only the team copy is sent.", enquiry.getId());
    }
    if (notifyEmail != null && !notifyEmail.isBlank()) {
      sender.send(teamNotice(enquiry));
    }
  }

  BrandedMail receipt(ListingEnquiry e) {
    return BrandedMail.create()
        .to(e.getEmail())
        .replyTo(replyTo)
        .subject("We received your enquiry - " + subject(e))
        .preheader(companyName(e) + " has your message and will reply by email.")
        .title("We received your enquiry")
        .greeting("Hello " + e.getName() + ",")
        .paragraph(
            "Thank you for your enquiry on Ethio Build Connect. "
                + companyName(e)
                + " has been sent your message and will reply to you directly.")
        .detail("About", subject(e))
        .detail("Page", pageUrl(e))
        .detail("Your email", e.getEmail())
        .detail("Your phone", e.getPhoneNumber())
        .message("Your message", e.getMessage())
        .cta("View the listing", pageUrl(e))
        .closing(
            "If you do not hear back within 2 business days, reply to this email and our team will"
                + " follow up for you.");
  }

  BrandedMail companyNotice(ListingEnquiry e, String to) {
    return BrandedMail.create()
        .to(to)
        .replyTo(e.getEmail())
        .subject("New enquiry: " + subject(e) + " - " + e.getName())
        .preheader(e.getName() + " asked about " + subject(e) + ". Reply to answer them.")
        .title("New enquiry about " + subject(e))
        .paragraph(
            "A visitor on Ethio Build Connect asked about "
                + subject(e)
                + ". Reply to this email to answer them directly.")
        .detail("Name", e.getName())
        .detail("Email", e.getEmail())
        .detail("Phone", e.getPhoneNumber())
        .detail("Page", pageUrl(e))
        .message("Message", e.getMessage())
        .cta("Reply to " + firstName(e), "mailto:" + e.getEmail())
        .closing("A quick, friendly reply is the best way to turn an enquiry into a visit.");
  }

  BrandedMail teamNotice(ListingEnquiry e) {
    String company =
        companyName(e)
            + (companyEmail(e) != null
                ? " (" + companyEmail(e) + ")"
                : " (no email on file - please forward)");
    return BrandedMail.create()
        .to(notifyEmail.split("\\s*,\\s*"))
        .replyTo(e.getEmail())
        .subject("Listing enquiry: " + subject(e) + " - " + e.getEmail())
        .preheader("Copy of an enquiry sent to " + companyName(e) + ".")
        .title("Listing enquiry")
        .paragraph(
            "Copy of an enquiry sent to the listing's company. Track it in the admin portal.")
        .detail("About", subject(e))
        .detail("Company", company)
        .detail("Name", e.getName())
        .detail("Email", e.getEmail())
        .detail("Phone", e.getPhoneNumber())
        .detail("Page", pageUrl(e))
        .message("Message", e.getMessage())
        .cta("Open enquiries", base() + "/admin/enquiries");
  }

  private String base() {
    return frontendBaseUrl.replaceAll("/$", "");
  }

  private String pageUrl(ListingEnquiry e) {
    if (notBlank(e.getPageUrl())) return e.getPageUrl();
    if (e.getProperty() != null) return base() + "/properties/" + e.getProperty().getId();
    if (e.getOrganization() != null) {
      return base() + "/organizations/" + e.getOrganization().getId();
    }
    return base();
  }

  private static String subject(ListingEnquiry e) {
    if (notBlank(e.getSubjectTitle())) return e.getSubjectTitle();
    return e.getSubjectType() == ListingEnquiry.SubjectType.PROPERTY ? "a property" : "a company";
  }

  private static String companyName(ListingEnquiry e) {
    Organization o = e.getOrganization();
    return o != null && notBlank(o.getName()) ? o.getName() : "The lister";
  }

  private static String firstName(ListingEnquiry e) {
    String n = e.getName() == null ? "" : e.getName().trim();
    int sp = n.indexOf(' ');
    return sp > 0 ? n.substring(0, sp) : (n.isEmpty() ? "the visitor" : n);
  }

  private static String companyEmail(ListingEnquiry e) {
    Organization o = e.getOrganization();
    if (o == null || o.getContact() == null) return null;
    String email = o.getContact().getEmail();
    return notBlank(email) ? email.trim() : null;
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
