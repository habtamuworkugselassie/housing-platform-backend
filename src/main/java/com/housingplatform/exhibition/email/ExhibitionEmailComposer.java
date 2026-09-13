package com.housingplatform.exhibition.email;

import com.housingplatform.exhibition.config.ExpoProperties;
import com.housingplatform.exhibition.domain.ExhibitionEmailKind;
import com.housingplatform.exhibition.domain.ExhibitionInterest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Writes the lifecycle mails.
 *
 * <p>Pure: give it a registrant and a kind and it returns a subject and a body. It sends nothing,
 * reads no database and has no clock, which is what lets the copy be asserted in tests rather than
 * proof-read on a staging inbox.
 *
 * <p><b>Plain text, deliberately.</b> Every other mail this platform sends is a {@code
 * SimpleMailMessage} and these match. A brand-new sending domain with no reputation gets filtered
 * on the strength of what it looks like, and a text mail that reads as though a person wrote it
 * clears filters an image-heavy HTML template does not. It also cannot render badly.
 *
 * <p><b>Nothing is claimed that the site does not say.</b> No exhibitor counts, no visitor numbers,
 * no "free entry" — the same rule {@code GOOGLE-ADS.md} applies to ad copy, for the same reason:
 * the landing page has to substantiate it, and today it does not.
 */
@Component
@RequiredArgsConstructor
public class ExhibitionEmailComposer {

  private static final DateTimeFormatter DAY_MONTH_YEAR =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
  private static final DateTimeFormatter MONTH_YEAR =
      DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d", Locale.ENGLISH);

  private final ExpoProperties expo;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  public ExhibitionEmailContent compose(ExhibitionInterest interest, ExhibitionEmailKind kind) {
    return switch (kind) {
      case CONFIRMATION -> confirmation(interest);
      case REMINDER_T30 -> reminderT30(interest);
      case REMINDER_T7 -> reminderT7(interest);
      case REMINDER_T1 -> reminderT1(interest);
    };
  }

  // --- Confirmation --------------------------------------------------------

  /**
   * The mail that closes the loop. Before this existed, a registration was answered with silence
   * until an admin happened to work the queue — so the three things it has to do are confirm that
   * the form arrived, say what happens next and by when, and give exactly one thing to do now.
   */
  private ExhibitionEmailContent confirmation(ExhibitionInterest interest) {
    String type = interestType(interest);
    StringBuilder b = new StringBuilder();

    switch (type) {
      case "exhibitor" -> {
        b.append("Thank you for your interest in exhibiting at ")
            .append(expo.getName())
            .append(".\n\n")
            .append(eventLine())
            .append("\n\n");
        if (packageName(interest) != null) {
          b.append("Package you asked about: ").append(packageName(interest)).append("\n\n");
        }
        b.append(
            "What happens next: our exhibition team reviews your details and contacts you"
                + " within two working days to confirm availability, answer questions about"
                + " stand space and take you through the next steps.\n\n");
        b.append("While you wait, the packages and what each includes are here:\n")
            .append(link("/sponsorships", ExhibitionEmailKind.CONFIRMATION))
            .append('\n');
      }
      case "partner" -> {
        b.append("Thank you for proposing a partnership with ")
            .append(expo.getName())
            .append(".\n\n")
            .append(eventLine())
            .append("\n\n");
        b.append(
            "What happens next: our partnerships team reads every proposal properly rather"
                + " than sending a form reply, so allow up to three working days. We will come"
                + " back to you on fit, visibility and what we can offer in return.\n\n");
        b.append("The current partnership and sponsorship packages are here:\n")
            .append(link("/sponsorships", ExhibitionEmailKind.CONFIRMATION))
            .append('\n');
      }
      default -> {
        b.append("Thank you for registering your interest in visiting ")
            .append(expo.getName())
            .append(".\n\n")
            .append(eventLine())
            .append("\n\n");
        b.append(
            "What happens next: we will email you when the programme and the exhibitor list"
                + " are published, and again shortly before the doors open. Nothing else is"
                + " needed from you now.\n\n");
        b.append(
                "In the meantime you can browse verified property listings, developers,"
                    + " contractors and suppliers on the platform year round:\n")
            .append(link("/properties", ExhibitionEmailKind.CONFIRMATION))
            .append('\n');
      }
    }

    b.append(signature(interest, ExhibitionEmailKind.CONFIRMATION));

    String subject =
        switch (type) {
          case "exhibitor" -> "We have your exhibitor enquiry - " + expo.getName();
          case "partner" -> "We have your partnership proposal - " + expo.getName();
          default -> "You are registered - " + expo.getName() + ", " + dateRange();
        };
    return new ExhibitionEmailContent(subject, b.toString());
  }

  // --- Reminders -----------------------------------------------------------

  /** A month out: the last comfortable moment to decide to take part rather than just attend. */
  private ExhibitionEmailContent reminderT30(ExhibitionInterest interest) {
    String type = interestType(interest);
    StringBuilder b = new StringBuilder();
    b.append(expo.getName()).append(" opens in one month.\n\n").append(eventLine()).append("\n\n");

    switch (type) {
      case "exhibitor" -> b.append(
              "This is the point where stand space and the printed programme get locked"
                  + " down. If anything about your plans has changed - more space, a"
                  + " different package, someone else handling it - tell us now while it is"
                  + " still easy to change.\n\n")
          .append("Packages and what each includes:\n")
          .append(link("/sponsorships", ExhibitionEmailKind.REMINDER_T30));
      case "partner" -> b.append(
              "Partnership visibility - logos, programme mentions, stage time - is"
                  + " arranged around this point in the schedule. If you are still deciding,"
                  + " a month out is the last comfortable moment to talk.\n\n")
          .append("Partnership and sponsorship packages:\n")
          .append(link("/sponsorships", ExhibitionEmailKind.REMINDER_T30));
      default -> b.append(
              "Three days of developers, contractors, banks, insurers and suppliers under"
                  + " one roof. Worth deciding now who you want to meet, and bringing"
                  + " whoever makes the decision with you.\n\n")
          .append("Browse who is on the platform before you come:\n")
          .append(link("/marketplace/contractors", ExhibitionEmailKind.REMINDER_T30));
    }

    b.append('\n').append(signature(interest, ExhibitionEmailKind.REMINDER_T30));
    return new ExhibitionEmailContent(
        "One month to " + expo.getName() + " - " + dateRange(), b.toString());
  }

  /** A week out: stop persuading, start helping them turn up. */
  private ExhibitionEmailContent reminderT7(ExhibitionInterest interest) {
    String type = interestType(interest);
    StringBuilder b = new StringBuilder();
    b.append(expo.getName()).append(" opens in one week.\n\n").append(eventLine()).append("\n\n");

    switch (type) {
      case "exhibitor", "partner" -> b.append(
          "A week out, the things worth checking: who from your team is attending, what"
              + " you are bringing to show, and that we have the right phone number for"
              + " whoever will be on site. Reply to this email if anything needs"
              + " changing.\n");
      default -> b.append(
          "A week out, the things worth planning: which days you can come, who you want"
              + " to meet, and getting to the venue - traffic in Addis Ababa around the"
              + " venue is worth allowing for.\n");
    }

    b.append("\nThe full event details are on the site:\n")
        .append(link("/", ExhibitionEmailKind.REMINDER_T7))
        .append('\n')
        .append(signature(interest, ExhibitionEmailKind.REMINDER_T7));
    return new ExhibitionEmailContent(
        "One week to " + expo.getName() + " - " + dateRange(), b.toString());
  }

  /** The day before: no persuasion at all, only what someone needs to walk in tomorrow. */
  private ExhibitionEmailContent reminderT1(ExhibitionInterest interest) {
    StringBuilder b = new StringBuilder();
    b.append(expo.getName())
        .append(" opens tomorrow.\n\n")
        .append(eventLine())
        .append("\n\n")
        .append("Bring a form of identification and business cards if you have them.\n\n")
        .append("Everything else, including how to reach the venue:\n")
        .append(link("/", ExhibitionEmailKind.REMINDER_T1))
        .append('\n')
        .append(signature(interest, ExhibitionEmailKind.REMINDER_T1));
    return new ExhibitionEmailContent(
        expo.getName() + " opens tomorrow - " + expo.getVenue(), b.toString());
  }

  // --- Shared pieces -------------------------------------------------------

  private String eventLine() {
    return dateRange() + "\n" + expo.getVenue() + ", " + expo.getCity();
  }

  /**
   * "16-18 November 2026" when the expo sits inside one month, and the long form when it does not.
   * The site's hero renders the same span, and event markup is held to matching the visible page.
   */
  String dateRange() {
    LocalDate start = expo.getStartDate();
    LocalDate end = expo.getEndDate();
    if (start.equals(end)) {
      return DAY_MONTH_YEAR.format(start);
    }
    if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()) {
      return DAY.format(start) + "-" + DAY.format(end) + " " + MONTH_YEAR.format(start);
    }
    return DAY_MONTH_YEAR.format(start) + " - " + DAY_MONTH_YEAR.format(end);
  }

  private String signature(ExhibitionInterest interest, ExhibitionEmailKind kind) {
    StringBuilder b = new StringBuilder("\n--\n").append(expo.getName()).append('\n');
    String replyTo = expo.getLifecycleEmails().getReplyTo();
    if (replyTo != null && !replyTo.isBlank()) {
      b.append("Questions? Reply to this email or write to ").append(replyTo).append('\n');
    } else {
      b.append("Questions? Reply to this email.\n");
    }
    if (kind.isReminder()) {
      // Only the reminder series is a series. The confirmation answers something the person did
      // a moment ago, and offering to unsubscribe from a receipt would be a strange thing to do.
      b.append("\nTo stop receiving reminders about this event:\n")
          .append(unsubscribeUrl(interest))
          .append('\n');
    }
    return b.toString();
  }

  /**
   * Site links carry their own campaign tags, so a click from the T-7 mail shows up in Analytics as
   * exactly that rather than as direct traffic. Same vocabulary the ads use, so email and paid
   * search can be compared side by side.
   */
  String link(String path, ExhibitionEmailKind kind) {
    String base = baseUrl() + path;
    String separator = base.contains("?") ? "&" : "?";
    return base
        + separator
        + "utm_source=lifecycle&utm_medium=email&utm_campaign=expo-"
        + expo.getStartDate().getYear()
        + "&utm_content="
        + kind.name().toLowerCase(Locale.ROOT);
  }

  String unsubscribeUrl(ExhibitionInterest interest) {
    return baseUrl()
        + "/api/v1/exhibition/interest/unsubscribe?token="
        + URLEncoder.encode(
            interest.getUnsubscribeToken() == null ? "" : interest.getUnsubscribeToken(),
            StandardCharsets.UTF_8);
  }

  private String baseUrl() {
    return frontendBaseUrl == null ? "" : frontendBaseUrl.replaceAll("/$", "");
  }

  private static String interestType(ExhibitionInterest interest) {
    String type = interest.getInterestType();
    return type == null ? "visitor" : type.trim().toLowerCase(Locale.ROOT);
  }

  private static String packageName(ExhibitionInterest interest) {
    return interest.getSponsorship() == null ? null : interest.getSponsorship().getName();
  }
}
