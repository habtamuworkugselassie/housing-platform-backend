package com.housingplatform.outreach.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends one outreach email through the configured SMTP relay (Brevo in production): a plain-text
 * part, the branded HTML part, and the package PDFs as attachments. Returns the Message-ID so the
 * follow-up can thread onto it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutreachMailer {

  public record Options(
      boolean includePackages,
      boolean attachBrochure,
      boolean attachSponsorship,
      boolean attachAgreement) {
    /** First email: package table in the body, brochure, package sheet and agreement attached. */
    public static Options firstEmail() {
      return new Options(true, true, true, true);
    }

    public static Options followUp() {
      return new Options(false, false, false, false);
    }
  }

  private final JavaMailSender mailSender;
  private final OutreachProperties props;

  @Value("${app.mail.from:${spring.mail.username:}}")
  private String defaultFrom;

  @Value("${app.mail.reply-to:}")
  private String defaultReplyTo;

  @Value("${app.mail.logo-url:}")
  private String logoUrl;

  public boolean configured() {
    return !from().isBlank();
  }

  public String from() {
    return props.getSenderEmail().isBlank()
        ? (defaultFrom == null ? "" : defaultFrom)
        : props.getSenderEmail();
  }

  public String replyTo() {
    return props.getReplyTo().isBlank()
        ? (defaultReplyTo == null ? "" : defaultReplyTo)
        : props.getReplyTo();
  }

  /**
   * @return the Message-ID header of the sent mail.
   */
  public String send(
      String to,
      String subject,
      String bodyText,
      String locale,
      String registerUrl,
      String inReplyTo,
      Options options)
      throws Exception {
    String site = props.getSiteUrl().replaceAll("/$", "");
    String logo =
        logoUrl == null || logoUrl.isBlank()
            ? site + "/images/branding/ethio-build-connect-wordmark.png"
            : logoUrl;
    String messageId = "<outreach-" + UUID.randomUUID() + "@" + domainOf(from()) + ">";

    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
    helper.setFrom(
        new InternetAddress(from(), props.getSenderName(), StandardCharsets.UTF_8.name()));
    helper.setTo(to);
    if (!replyTo().isBlank()) helper.setReplyTo(replyTo());
    helper.setSubject(subject);
    String text =
        bodyText.trim()
            + (options.includePackages() ? "\n\n" + OutreachPackages.plainText(locale) : "")
            + "\n";
    helper.setText(
        text,
        OutreachHtml.render(bodyText, locale, options.includePackages(), registerUrl, site, logo));
    message.setHeader("Message-ID", messageId);
    if (inReplyTo != null && !inReplyTo.isBlank()) {
      message.setHeader("In-Reply-To", inReplyTo);
      message.setHeader("References", inReplyTo);
    }
    for (String name : attachmentNames(locale, options)) {
      ClassPathResource res = new ClassPathResource("outreach/attachments/" + name);
      if (res.exists()) {
        helper.addAttachment(displayName(name), res, "application/pdf");
      } else {
        log.warn("Outreach attachment missing: {}", name);
      }
    }
    mailSender.send(message);
    log.info("Outreach mail '{}' sent to {}", subject, to);
    return messageId;
  }

  public static List<String> attachmentNames(String locale, Options o) {
    String l = com.housingplatform.shared.mail.MailLocale.normalize(locale);
    List<String> names = new ArrayList<>();
    if (o.attachBrochure()) names.add("brochure-" + l + ".pdf");
    if (o.attachSponsorship()) names.add("sponsorship-" + l + ".pdf");
    if (o.attachAgreement()) names.add("agreement-" + l + ".pdf");
    return names;
  }

  public static String displayName(String file) {
    String base = file.replace(".pdf", "");
    String lang = base.endsWith("-am") ? "Amharic" : "English";
    String kind =
        base.startsWith("brochure")
            ? "Brochure"
            : base.startsWith("sponsorship") ? "Sponsorship packages" : "Exhibitor agreement";
    return "Ethio Build Connect Expo - " + kind + " (" + lang + ").pdf";
  }

  private static String domainOf(String email) {
    int at = email.indexOf('@');
    return at < 0 ? "ethiobuildconnect.et" : email.substring(at + 1);
  }
}
