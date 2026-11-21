package com.housingplatform.visitor.service;

import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import com.housingplatform.shared.mail.MailLocale;
import com.housingplatform.visitor.domain.VisitorRegistration;
import com.housingplatform.visitor.repository.VisitorRegistrationRepository;
import com.housingplatform.visitor.util.QrCodes;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Emails the badge (QR inline) after a registration is committed; failures are only logged. */
@Service
@RequiredArgsConstructor
@Slf4j
public class VisitorBadgeMailService {

  private final BrandedMailSender sender;
  private final VisitorRegistrationRepository repository;

  @Value("${app.mail.reply-to:}")
  private String replyTo;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  public String badgeUrl(VisitorRegistration v) {
    String site = frontendBaseUrl.replaceAll("/$", "");
    String prefix = MailLocale.isAmharic(v.getLocale()) ? site + "/am" : site;
    return prefix + "/badge/" + v.getCode();
  }

  @Async
  public void sendBadge(VisitorRegistration v) {
    if (!sender.configured()) {
      log.warn("Mail not configured; badge {} for {} not emailed", v.getCode(), v.getEmail());
      return;
    }
    boolean ok = sender.send(build(v));
    if (ok) {
      repository
          .findById(v.getId())
          .ifPresent(
              fresh -> {
                fresh.setBadgeSentAt(LocalDateTime.now());
                repository.save(fresh);
              });
    }
  }

  BrandedMail build(VisitorRegistration v) {
    byte[] qr = QrCodes.png(badgeUrl(v), 440);
    boolean am = MailLocale.isAmharic(v.getLocale());
    String first = firstName(v.getFullName());
    if (am) {
      return BrandedMail.create()
          .lang("am")
          .to(v.getEmail())
          .replyTo(replyTo)
          .subject("የኤክስፖ መግቢያ ባጅዎ · " + v.getCode())
          .preheader("ጥር 01–05, 2019 ዓ.ም · አዲስ ዓለም አቀፍ ኮንቬንሽን ሴንተር። ይህን QR በመግቢያው ያሳዩ።")
          .title("ለኢትዮ ቢልድ ኮኔክት ኤክስፖ ተመዝግበዋል")
          .greeting("ሰላም " + first + "፣")
          .paragraph("እንኳን ደህና መጡ! ይህ የመግቢያ ባጅዎ ነው። በመግቢያው ላይ ከስልክዎ ላይ QR ኮዱን ያሳዩ፤ ህትመት አያስፈልግም።")
          .detail("ስም", v.getFullName())
          .detail("ድርጅት", v.getOrganization())
          .detail("ቀን", "ጥር 01–05, 2019 ዓ.ም (9–13 January 2027)")
          .detail("ቦታ", "አዲስ ዓለም አቀፍ ኮንቬንሽን ሴንተር (AICC)፣ አዲስ አበባ")
          .detail("የባጅ ኮድ", v.getCode())
          .inlineImage("badge", qr, "የመግቢያ QR ኮድ", v.getCode())
          .cta("ባጅዎን ይክፈቱ", badgeUrl(v))
          .closing("ይህን ኢሜይል ያስቀምጡ ወይም ባጁን ያትሙ። ጥያቄ ካለዎት ይህን ኢሜይል ይመልሱ።")
          .closing("ኢትዮ ቢልድ ኮኔክት ኤክስፖ · ድሪም ቲም ትሬዲንግ ኃ/የተ/የግ/ማህበር");
    }
    return BrandedMail.create()
        .to(v.getEmail())
        .replyTo(replyTo)
        .subject("Your expo visitor badge · " + v.getCode())
        .preheader(
            "9–13 January 2027 · Addis International Convention Center. Show this QR at the gate.")
        .title("You are registered for Ethio Build Connect Expo")
        .greeting("Hello " + first + ",")
        .paragraph(
            "Welcome! This is your visitor badge. Show the QR code on your phone at the entrance; no printing needed.")
        .detail("Name", v.getFullName())
        .detail("Organization", v.getOrganization())
        .detail("Dates", "9–13 January 2027 (Tir 01–05, 2019 E.C.)")
        .detail("Venue", "Addis International Convention Center (AICC), Addis Ababa")
        .detail("Badge code", v.getCode())
        .inlineImage("badge", qr, "Entry QR code", v.getCode())
        .cta("Open your badge", badgeUrl(v))
        .closing("Keep this email, or print the badge page. Reply to this email with any question.")
        .closing("Ethio Build Connect Expo · Dream Team Trading PLC");
  }

  private static String firstName(String full) {
    String f = full == null ? "" : full.trim();
    int sp = f.indexOf(' ');
    return sp > 0 ? f.substring(0, sp) : f;
  }
}
