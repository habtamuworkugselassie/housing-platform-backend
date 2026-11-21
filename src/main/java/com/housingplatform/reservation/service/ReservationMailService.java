package com.housingplatform.reservation.service;

import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import com.housingplatform.shared.mail.MailLocale;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Emails around a reservation: the confirmation with the agreement and package PDFs and the bank
 * details, the team notice, and the follow-up reminder. Sent off the request thread; failures are
 * logged only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationMailService {

  private final BrandedMailSender sender;
  private final SponsorshipReservationRepository repository;

  @Value("${app.exhibition.notify-email:}")
  private String notifyEmail;

  @Value("${app.mail.reply-to:}")
  private String replyTo;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Async
  public void sendConfirmation(SponsorshipReservation r, boolean markAgreementSent) {
    sendConfirmation(r, markAgreementSent, true);
  }

  @Async
  public void sendConfirmation(
      SponsorshipReservation r, boolean markAgreementSent, boolean notifyTeam) {
    if (!sender.configured()) {
      log.warn("Mail not configured; reservation {} not emailed", r.getReference());
      return;
    }
    boolean ok = sender.send(confirmation(r));
    if (ok && markAgreementSent) {
      repository
          .findById(r.getId())
          .ifPresent(
              fresh -> {
                fresh.setAgreementSentAt(LocalDateTime.now());
                if (fresh.getStatus() == SponsorshipReservation.Status.REQUESTED) {
                  fresh.setStatus(SponsorshipReservation.Status.AGREEMENT_SENT);
                  fresh.setStatusChangedAt(LocalDateTime.now());
                }
                repository.save(fresh);
              });
    }
    if (notifyTeam && notifyEmail != null && !notifyEmail.isBlank()) sender.send(teamNotice(r));
  }

  @Async
  public void sendReminder(SponsorshipReservation r) {
    if (!sender.configured()) return;
    sender.send(reminder(r));
  }

  BrandedMail confirmation(SponsorshipReservation r) {
    boolean am = MailLocale.isAmharic(r.getLocale());
    BrandedMail m =
        am
            ? BrandedMail.create()
                .lang("am")
                .to(r.getEmail())
                .replyTo(replyTo)
                .subject("የስፖንሰርሺፕ ቦታ ማስያዝ · " + r.getPackageName() + " · " + r.getReference())
                .preheader("ውሉ እና የክፍያ ዝርዝሩ ተያይዘዋል። ቦታዎ ክፍያ ሲረጋገጥ ይጸናል።")
                .title("ቦታ ማስያዝዎ ደርሶናል")
                .greeting("ሰላም " + r.getContactName() + "፣")
                .paragraph(
                    "ለ"
                        + r.getPackageName()
                        + " ፓኬጅ ያቀረቡትን ጥያቄ ተቀብለናል። የስፖንሰርሺፕ ውሉ እና የፓኬጅ ዝርዝሩ ከዚህ ኢሜይል ጋር ተያይዘዋል። ውሉን ፈርመው ለዚህ ኢሜይል ይመልሱ፤ ቦታዎ ክፍያው ሲረጋገጥ ይጸናል (ቦታዎች እንደ መጡበት ቅደም ተከተል ይሰጣሉ)።")
                .detail("ማጣቀሻ", r.getReference())
                .detail("ፓኬጅ", r.getPackageName())
                .detail("ዋጋ", money(r.getAmount()) + " " + r.getCurrency() + " (ተ.እ.ታ. ሳይጨምር)")
                .detail("ድርጅት", r.getCompany())
                .detail("የዳስ ምርጫ", r.getBoothPreference())
            : BrandedMail.create()
                .to(r.getEmail())
                .replyTo(replyTo)
                .subject(
                    "Sponsorship reservation · " + r.getPackageName() + " · " + r.getReference())
                .preheader(
                    "Agreement and payment details attached. Your place is confirmed once payment clears.")
                .title("We have your reservation")
                .greeting("Hello " + r.getContactName() + ",")
                .paragraph(
                    "Thank you for reserving the "
                        + r.getPackageName()
                        + " package for Ethio Build Connect Expo, 9–13 January 2027 at the Addis International Convention Center. The sponsorship agreement and the package sheet are attached. Sign the agreement and reply to this email; your place is confirmed once payment is received (places are allocated first come, first served).")
                .detail("Reference", r.getReference())
                .detail("Package", r.getPackageName())
                .detail("Amount", money(r.getAmount()) + " " + r.getCurrency() + " (excl. VAT)")
                .detail("Company", r.getCompany())
                .detail("Booth preference", r.getBoothPreference());
    bankDetails(m, am);
    m.cta(
        am ? "ፓኬጆቹን በመስመር ላይ ይመልከቱ" : "See the packages online",
        base() + (am ? "/am" : "") + "/#sponsorship-packages");
    m.closing(
        am
            ? "ሙሉ (100%) ክፍያ ውሉ ከተፈረመ በ10 የስራ ቀናት ውስጥ ይፈጸማል። ጥያቄ ካለዎት ይህን ኢሜይል ይመልሱ ወይም +251 913 504 097 ይደውሉ።"
            : "Full (100%) payment is due within 10 working days of signing. Reply to this email or call +251 913 504 097 with any question.");
    m.closing(
        am
            ? "ድሪም ቲም ትሬዲንግ ኃ/የተ/የግ/ማህበር · ኢትዮ ቢልድ ኮኔክት ኤክስፖ"
            : "Dream Team Trading PLC · Ethio Build Connect Expo");
    attach(
        m,
        "agreement-" + (am ? "am" : "en") + ".pdf",
        am ? "የአሳያጅ ውል.pdf" : "Ethio Build Connect Expo - Exhibitor agreement.pdf");
    attach(
        m,
        "sponsorship-" + (am ? "am" : "en") + ".pdf",
        am ? "የስፖንሰርሺፕ ፓኬጆች.pdf" : "Ethio Build Connect Expo - Sponsorship packages.pdf");
    return m;
  }

  BrandedMail reminder(SponsorshipReservation r) {
    boolean am = MailLocale.isAmharic(r.getLocale());
    BrandedMail m =
        am
            ? BrandedMail.create()
                .lang("am")
                .to(r.getEmail())
                .replyTo(replyTo)
                .subject("አስታዋሽ፦ " + r.getPackageName() + " · " + r.getReference())
                .title("ቦታዎ አሁንም ተይዞልዎታል")
                .greeting("ሰላም " + r.getContactName() + "፣")
                .paragraph(
                    "ለ"
                        + r.getPackageName()
                        + " ፓኬጅ ያስያዙት ቦታ አሁንም ክፍት ነው። የተፈረመውን ውል እና ክፍያውን እንዳገኘን ቦታዎ ይጸናል። ቦታዎች ውስን ሲሆኑ እንደ መጡበት ቅደም ተከተል ይሰጣሉ።")
                .detail("ማጣቀሻ", r.getReference())
                .detail("ዋጋ", money(r.getAmount()) + " " + r.getCurrency())
            : BrandedMail.create()
                .to(r.getEmail())
                .replyTo(replyTo)
                .subject("Reminder: " + r.getPackageName() + " · " + r.getReference())
                .title("Your place is still held")
                .greeting("Hello " + r.getContactName() + ",")
                .paragraph(
                    "A short reminder that your reservation for the "
                        + r.getPackageName()
                        + " package is still open. Your place is confirmed once we receive the signed agreement and payment. Places are limited and allocated first come, first served.")
                .detail("Reference", r.getReference())
                .detail("Amount", money(r.getAmount()) + " " + r.getCurrency());
    bankDetails(m, am);
    m.closing(
        am
            ? "ጥያቄ ካለዎት ይህን ኢሜይል ይመልሱ።"
            : "Reply to this email if anything is unclear or if your plans have changed.");
    if (notifyEmail != null && !notifyEmail.isBlank()) m.bcc(notifyEmail.split("\\s*,\\s*"));
    return m;
  }

  BrandedMail teamNotice(SponsorshipReservation r) {
    return BrandedMail.create()
        .to(notifyEmail.split("\\s*,\\s*"))
        .replyTo(r.getEmail())
        .subject("Package reservation: " + r.getPackageName() + " - " + r.getCompany())
        .preheader(money(r.getAmount()) + " " + r.getCurrency() + " · " + r.getReference())
        .title("New package reservation")
        .paragraph(
            "A sponsor reserved a package from the website. The agreement and bank details were emailed to them.")
        .detail("Reference", r.getReference())
        .detail("Package", r.getPackageName())
        .detail("Amount", money(r.getAmount()) + " " + r.getCurrency())
        .detail("Company", r.getCompany())
        .detail("Contact", r.getContactName())
        .detail("Email", r.getEmail())
        .detail("Phone", r.getPhone())
        .detail("TIN", r.getTin())
        .detail("Booth preference", r.getBoothPreference())
        .message("Message", r.getMessage())
        .cta("Open the pipeline", base() + "/admin/sponsorship-reservations");
  }

  private static void bankDetails(BrandedMail m, boolean am) {
    m.detail(am ? "ተከፋይ" : "Payee", SponsorshipPaymentDetails.PAYEE);
    for (SponsorshipPaymentDetails.Account a : SponsorshipPaymentDetails.ACCOUNTS) {
      m.detail(am ? a.bankAm() : a.bank(), a.number());
    }
  }

  private static void attach(BrandedMail m, String resource, String filename) {
    try {
      ClassPathResource res = new ClassPathResource("outreach/attachments/" + resource);
      if (res.exists()) m.attach(filename, res.getContentAsByteArray(), "application/pdf");
    } catch (IOException e) {
      log.warn("Could not attach {}: {}", resource, e.getMessage());
    }
  }

  static String money(BigDecimal v) {
    if (v == null) return "0";
    NumberFormat f = NumberFormat.getIntegerInstance(Locale.UK);
    return f.format(v.setScale(0, java.math.RoundingMode.HALF_UP));
  }

  private String base() {
    return frontendBaseUrl.replaceAll("/$", "");
  }
}
