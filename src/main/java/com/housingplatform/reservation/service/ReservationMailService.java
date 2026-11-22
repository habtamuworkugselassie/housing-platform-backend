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
  public void sendPaymentReceipt(
      SponsorshipReservation r, com.housingplatform.reservation.domain.ReservationPayment p) {
    if (!sender.configured()) return;
    boolean am = MailLocale.isAmharic(r.getLocale());
    boolean full = r.getStatus() == SponsorshipReservation.Status.PAID;
    BrandedMail m =
        BrandedMail.create()
            .lang(am ? "am" : "en")
            .to(r.getEmail())
            .replyTo(replyTo)
            .subject((am ? "ክፍያ ደርሶናል · " : "Payment received · ") + r.getReference())
            .title(
                am
                    ? (full ? "ሙሉ ክፍያዎ ደርሶናል፤ ቦታዎ ተረጋግጧል" : "ቅድመ ክፍያዎ ደርሶናል፤ ቦታዎ ተይዟል")
                    : (full
                        ? "Paid in full: your place is confirmed"
                        : "Deposit received: your booths are secured"))
            .greeting((am ? "ሰላም " : "Hello ") + r.getContactName() + (am ? "፣" : ","))
            .paragraph(
                am
                    ? "ለ"
                        + r.getPackageName()
                        + " ፓኬጅ "
                        + money(p.getAmount())
                        + " "
                        + p.getCurrency()
                        + " በመስመር ላይ ተቀብለናል።"
                        + (full ? "" : " ቀሪው ክፍያ ውሉ ከተፈረመ በ10 የስራ ቀናት ውስጥ ይፈጸማል።")
                    : "We received "
                        + money(p.getAmount())
                        + " "
                        + p.getCurrency()
                        + " online for the "
                        + r.getPackageName()
                        + " package."
                        + (full
                            ? ""
                            : " The balance is due within 10 working days of signing the agreement."))
            .detail(am ? "ማጣቀሻ" : "Reference", r.getReference())
            .detail(am ? "የተከፈለ" : "Paid so far", money(r.getAmountPaid()) + " " + r.getCurrency())
            .detail(
                am ? "ቀሪ" : "Outstanding",
                money(r.getAmount().subtract(r.getAmountPaid())) + " " + r.getCurrency())
            .detail(am ? "የክፍያ መለያ" : "Transaction", p.getTxRef());
    if (notifyEmail != null && !notifyEmail.isBlank()) m.bcc(notifyEmail.split("\\s*,\\s*"));
    sender.send(m);
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

  /** An admin invites a lead to reserve; the link lands on the form with everything prefilled. */
  @Async
  public void sendReserveInvite(com.housingplatform.exhibition.domain.ExhibitionInterest lead) {
    if (!sender.configured()) {
      log.warn("Mail not configured; reserve invite for {} not sent", lead.getEmail());
      return;
    }
    sender.send(reserveInvite(lead));
  }

  BrandedMail reserveInvite(com.housingplatform.exhibition.domain.ExhibitionInterest lead) {
    boolean am = MailLocale.isAmharic(lead.getLocale());
    StringBuilder url =
        new StringBuilder(frontendBaseUrl.replaceAll("/$", ""))
            .append(am ? "/am" : "")
            .append("/reserve?src=invite&email=")
            .append(
                java.net.URLEncoder.encode(
                    lead.getEmail(), java.nio.charset.StandardCharsets.UTF_8));
    if (lead.getSponsorship() != null)
      url.append("&package=").append(lead.getSponsorship().getId());
    if (lead.getOrganization() != null) url.append("&org=").append(lead.getOrganization().getId());
    if (lead.getCompany() != null && !lead.getCompany().isBlank())
      url.append("&company=")
          .append(
              java.net.URLEncoder.encode(
                  lead.getCompany(), java.nio.charset.StandardCharsets.UTF_8));
    String pkg = lead.getSponsorship() != null ? lead.getSponsorship().getName() : null;
    BrandedMail m =
        am
            ? BrandedMail.create()
                .lang("am")
                .to(lead.getEmail())
                .replyTo(replyTo)
                .subject("ቦታዎን ያስይዙ – Ethio Build Connect Expo 2027")
                .title("ቦታዎን ያስይዙ")
                .greeting("ሰላም፣")
                .paragraph(
                    "ለ Ethio Build Connect Expo 2027 ፍላጎት ስላሳዩ እናመሰግናለን። ቦታዎች ውስን ሲሆኑ እንደ መጡበት"
                        + " ቅደም ተከተል ይሰጣሉ። ከታች ባለው አገናኝ በሁለት ደቂቃ ውስጥ "
                        + (pkg != null ? pkg + " ጥቅል" : "ጥቅልዎን")
                        + " ያስይዙ፤ ውሉና የባንክ ዝርዝሮች ወዲያውኑ በኢሜይል ይደርስዎታል።")
                .cta("አሁን ያስይዙ", url.toString())
                .closing("ጥያቄ ካለዎት ይህን ኢሜይል ይመልሱ።")
            : BrandedMail.create()
                .to(lead.getEmail())
                .replyTo(replyTo)
                .subject("Reserve your place – Ethio Build Connect Expo 2027")
                .title("Reserve your place")
                .greeting("Hello,")
                .paragraph(
                    "Thank you for your interest in Ethio Build Connect Expo 2027. Places are limited"
                        + " and allocated first come, first served. The link below reserves "
                        + (pkg != null ? "the " + pkg + " package" : "your package")
                        + " in about two minutes; the agreement and bank details follow by email"
                        + " straight away.")
                .cta("Reserve now", url.toString())
                .closing("Reply to this email if you have any questions.");
    if (pkg != null) m.detail(am ? "ጥቅል" : "Package", pkg);
    if (notifyEmail != null && !notifyEmail.isBlank()) m.bcc(notifyEmail.split("\\s*,\\s*"));
    return m;
  }

  @Async
  public void sendPaymentDue(SponsorshipReservation r) {
    if (!sender.configured()) {
      log.warn("Mail not configured; payment-due email for {} not sent", r.getReference());
      return;
    }
    sender.send(paymentDue(r));
  }

  BrandedMail paymentDue(SponsorshipReservation r) {
    boolean am = MailLocale.isAmharic(r.getLocale());
    String payUrl =
        frontendBaseUrl.replaceAll("/$", "")
            + (am ? "/am" : "")
            + "/reserve/"
            + r.getReference()
            + "?email="
            + java.net.URLEncoder.encode(r.getEmail(), java.nio.charset.StandardCharsets.UTF_8);
    String due =
        r.getPaymentDueAt() == null
            ? ""
            : r.getPaymentDueAt()
                .toLocalDate()
                .format(
                    java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH));
    String outstanding = money(r.outstanding()) + " " + r.getCurrency();
    BrandedMail m =
        am
            ? BrandedMail.create()
                .lang("am")
                .to(r.getEmail())
                .replyTo(replyTo)
                .subject("ክፍያ ይጠበቃል፦ " + r.getPackageName() + " · " + r.getReference())
                .title("የቦታ ማስያዣዎ ክፍያ ይጠበቃል")
                .greeting("ሰላም " + r.getContactName() + "፣")
                .paragraph(
                    "ለ"
                        + r.getPackageName()
                        + " ጥቅል የፈረሙት ውል ክፍያ ቀን "
                        + due
                        + " አልፏል። ቀሪው "
                        + outstanding
                        + " እንደደረሰን ቦታዎ (እና ዳስዎ) ይረጋገጣል። ከታች ባለው አገናኝ በመስመር ላይ (በክፍያ ከ75,000 ብር"
                        + " ያልበለጠ) ወይም በባንክ ዝውውር መክፈል ይችላሉ።")
                .detail("ማጣቀሻ", r.getReference())
                .detail("ቀሪ ክፍያ", outstanding)
                .detail("የክፍያ ቀን", due)
                .cta("አሁን ይክፈሉ", payUrl)
            : BrandedMail.create()
                .to(r.getEmail())
                .replyTo(replyTo)
                .subject("Payment due: " + r.getPackageName() + " · " + r.getReference())
                .title("Your reservation payment is due")
                .greeting("Hello " + r.getContactName() + ",")
                .paragraph(
                    "The payment for your signed "
                        + r.getPackageName()
                        + " agreement was due on "
                        + due
                        + ". Your place (and booth) is confirmed as soon as the outstanding "
                        + outstanding
                        + " reaches us. You can pay online below (up to 75,000 ETB per transaction)"
                        + " or by bank transfer to the accounts listed.")
                .detail("Reference", r.getReference())
                .detail("Outstanding", outstanding)
                .detail("Due date", due)
                .cta("Pay now", payUrl);
    bankDetails(m, am);
    m.closing(
        am
            ? "ከከፈሉ ይህን ኢሜይል ችላ ይበሉ ወይም ደረሰኙን ይመልሱልን። ጥያቄ ካለዎት ይህን ኢሜይል ይመልሱ።"
            : "If you have already paid, ignore this or reply with the transfer slip. Reply to this"
                + " email with any question.");
    if (notifyEmail != null && !notifyEmail.isBlank()) m.bcc(notifyEmail.split("\\s*,\\s*"));
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
