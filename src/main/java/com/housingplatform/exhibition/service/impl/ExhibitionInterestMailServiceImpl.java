package com.housingplatform.exhibition.service.impl;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.service.ExhibitionInterestMailService;
import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import com.housingplatform.shared.mail.MailLocale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sent after an interest registration is committed. Runs off the request thread so a slow or
 * failing mail server never delays or fails the registration itself; failures are logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitionInterestMailServiceImpl implements ExhibitionInterestMailService {

  private final BrandedMailSender sender;

  /** Where the team is told about each registration; empty disables the notice. */
  @Value("${app.exhibition.notify-email:}")
  private String notifyEmail;

  /** Replies to receipts go here (the team inbox) when the sender is a no-reply address. */
  @Value("${app.mail.reply-to:}")
  private String replyTo;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  @Async
  @Override
  public void sendInterestEmails(ExhibitionInterest interest) {
    if (!sender.configured()) {
      log.warn(
          "Mail not configured. Interest registration {} ({}) not acknowledged by email.",
          interest.getEmail(),
          interest.getInterestType());
      return;
    }
    sender.send(receipt(interest));
    if (notifyEmail != null && !notifyEmail.isBlank()) {
      sender.send(teamNotice(interest));
    }
  }

  BrandedMail receipt(ExhibitionInterest i) {
    if (MailLocale.isAmharic(i.getLocale())) {
      return receiptAm(i);
    }
    BrandedMail m =
        BrandedMail.create()
            .to(i.getEmail())
            .replyTo(replyTo)
            .subject("We received your interest - Ethio Build Connect Expo 2027")
            .preheader(
                "Thank you for registering as a " + label(i.getInterestType()).toLowerCase() + ".")
            .title("Thank you for registering your interest")
            .greeting("Hello,")
            .paragraph(
                "We received your interest in the Ethio Build Connect Expo, 9-13 January 2027 at"
                    + " the Addis Convention Center, Addis Ababa.");
    details(m, i);
    String closingEn =
        "Our team replies to every registration within 2 business days. Until then you can"
            + " browse the exhibition, sponsorship packages and listings on the site.";
    if (!"visitor".equals(i.getInterestType())) {
      m.paragraph(
          "Ready to go further? Reserve your package online: you get the agreement and bank"
              + " details at once and can lock booths on the hall plan with a deposit.");
      return m.cta("Reserve a package", reserveUrl(i, false)).closing(closingEn);
    }
    return m.cta("Explore the expo", base()).closing(closingEn);
  }

  private BrandedMail receiptAm(ExhibitionInterest i) {
    BrandedMail m =
        BrandedMail.create()
            .lang(MailLocale.AM)
            .to(i.getEmail())
            .replyTo(replyTo)
            .subject("ፍላጎትዎ ደርሶናል - ኢትዮ ቢልድ ኮኔክት ኤክስፖ 2027")
            .preheader("እንደ " + labelAm(i.getInterestType()) + " ስለተመዘገቡ እናመሰግናለን።")
            .title("ፍላጎትዎን ስለመዘገቡ እናመሰግናለን")
            .greeting("ሰላም፣")
            .paragraph(
                "ከጥር 1 እስከ 5 ቀን 2019 ዓ.ም (9-13 ጃንዋሪ 2027) በአዲስ ኮንቬንሽን ሴንተር፣ አዲስ አበባ"
                    + " በሚካሄደው የኢትዮ ቢልድ ኮኔክት ኤክስፖ ላይ ያሳዩት ፍላጎት ደርሶናል።")
            .detail("ፍላጎት", labelAm(i.getInterestType()))
            .detail("ኢሜይል", i.getEmail())
            .detail("ስልክ", i.getPhoneNumber())
            .detail("ኩባንያ", i.getCompany());
    if (i.getSponsorship() != null) {
      m.detail("ጥቅል", i.getSponsorship().getName());
    }
    m.message("መልእክት", i.getMessage());
    String closingAm =
        "ቡድናችን ለእያንዳንዱ ምዝገባ በ2 የሥራ ቀናት ውስጥ ይመልሳል። እስከዚያው ኤግዚቢሽኑን፣ የስፖንሰርሺፕ"
            + " ጥቅሎችን እና ንብረቶችን በድረ-ገጹ ላይ መመልከት ይችላሉ።";
    if (!"visitor".equals(i.getInterestType())) {
      m.paragraph("ፓኬጅዎን በመስመር ላይ ያስይዙ፦ ውሉንና የባንክ መረጃውን ወዲያውኑ ያገኛሉ፣ በቅድመ ክፍያም ዳሶችን ማስጠበቅ ይችላሉ።");
      return m.cta("ፓኬጅ ያስይዙ", reserveUrl(i, true)).closing(closingAm);
    }
    return m.cta("ኤክስፖውን ይጎብኙ", base() + "/am").closing(closingAm);
  }

  BrandedMail teamNotice(ExhibitionInterest i) {
    BrandedMail m =
        BrandedMail.create()
            .to(notifyEmail.split("\\s*,\\s*"))
            .replyTo(i.getEmail())
            .subject("New expo interest: " + label(i.getInterestType()) + " - " + i.getEmail())
            .preheader("A new " + label(i.getInterestType()).toLowerCase() + " registration.")
            .title("New expo interest: " + label(i.getInterestType()))
            .paragraph("A new registration came in from the exhibition page.");
    details(m, i);
    return m.cta("Review in admin", base() + "/admin/exhibition-interests");
  }

  private static void details(BrandedMail m, ExhibitionInterest i) {
    m.detail("Interest", label(i.getInterestType()))
        .detail("Email", i.getEmail())
        .detail("Phone", i.getPhoneNumber())
        .detail("Company", i.getCompany());
    if (i.getSponsorship() != null) {
      m.detail("Package", i.getSponsorship().getName());
    }
    m.message("Message", i.getMessage());
  }

  private String base() {
    return frontendBaseUrl.replaceAll("/$", "");
  }

  private String reserveUrl(ExhibitionInterest i, boolean am) {
    String url = base() + (am ? "/am" : "") + "/reserve?src=interest";
    if (i.getSponsorship() != null) url += "&package=" + i.getSponsorship().getId();
    return url;
  }

  private static String labelAm(String interestType) {
    if (interestType == null) return "ጎብኚ";
    return switch (interestType) {
      case "exhibitor" -> "አሳያጅ";
      case "partner" -> "ስፖንሰር / አጋር";
      default -> "ጎብኚ";
    };
  }

  private static String label(String interestType) {
    if (interestType == null) return "Visitor";
    return switch (interestType) {
      case "exhibitor" -> "Exhibitor";
      case "partner" -> "Sponsor / partner";
      default -> "Visitor";
    };
  }
}
