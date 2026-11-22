package com.housingplatform.reservation.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.media.event.OrganizationMediaReviewedEvent;
import com.housingplatform.media.event.OrganizationMediaUploadedEvent;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import com.housingplatform.shared.mail.MailLocale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Exhibitor portal emails: the invitation after payment, the team notice when an exhibitor uploads
 * media, and the outcome of a media review. Failures are logged only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitorMailService {

  private final BrandedMailSender sender;
  private final OrganizationRepository organizations;

  @Value("${app.exhibition.notify-email:}")
  private String notifyEmail;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl;

  private String base() {
    return frontendBaseUrl.replaceAll("/$", "");
  }

  /** The set-password invitation; with a null token the user already has a password. */
  @Async
  public void sendInvite(SponsorshipReservation r, Organization org, User user, String token) {
    if (!sender.configured()) {
      log.warn("Mail not configured; exhibitor invite for {} not sent", r.getReference());
      return;
    }
    boolean am = MailLocale.isAmharic(r.getLocale());
    String url =
        token != null
            ? base() + "/reset-password?token=" + token + "&welcome=1"
            : base() + "/login";
    BrandedMail m = BrandedMail.create().to(user.getEmail()).lang(am ? "am" : "en");
    if (am) {
      m.subject("የአሳታፊ መግቢያዎ ዝግጁ ነው – Ethio Build Connect")
          .preheader("አርማዎን፣ መገለጫዎን፣ ፎቶዎችንና ቪዲዮዎችን ያስገቡ።")
          .title("የአሳታፊ መግቢያዎ ዝግጁ ነው")
          .greeting("ውድ " + r.getContactName() + "፣")
          .paragraph(
              "ለ "
                  + r.getPackageName()
                  + " ክፍያዎ ስለተመዘገበ ለ "
                  + org.getName()
                  + " የአሳታፊ መግቢያ ተከፍቷል። የድርጅትዎን መገለጫ ማስተካከል፣ አርማ፣ ፎቶዎችና ቪዲዮዎች መጫን"
                  + " ይችላሉ። የሚጫኑት ፋይሎች በቡድናችን ከተገመገሙ በኋላ በድረ-ገጹ ላይ ይታያሉ።")
          .detail("የመግቢያ ኢሜይል", user.getEmail())
          .detail("ድርጅት", org.getName())
          .detail("ጥቅል", r.getPackageName())
          .detail("ማጣቀሻ", r.getReference())
          .cta(token != null ? "የይለፍ ቃል ያዘጋጁ" : "ወደ መግቢያዎ ይግቡ", url)
          .closing(
              token != null
                  ? "አገናኙ ለ 72 ሰዓታት ይሰራል። ካለፈ፣ በመግቢያ ገጹ ላይ \"የይለፍ ቃል ረሳሁ\" ይጠቀሙ።"
                  : "በነባር የይለፍ ቃልዎ ይግቡ።")
          .closing("አገናኙ ካልሰራ ይህን ወደ አሳሽዎ ይቅዱ፦ " + url);
    } else {
      m.subject("Your exhibitor portal is ready – Ethio Build Connect")
          .preheader("Upload your logo, profile, photos and videos for the expo.")
          .title("Your exhibitor portal is ready")
          .greeting("Dear " + r.getContactName() + ",")
          .paragraph(
              "Your payment for "
                  + r.getPackageName()
                  + " is recorded, so an exhibitor account is now open for "
                  + org.getName()
                  + ". Use it to complete your company profile and upload your logo, photos and"
                  + " videos. Uploads appear on the website once our team has reviewed them.")
          .detail("Sign-in email", user.getEmail())
          .detail("Organization", org.getName())
          .detail("Package", r.getPackageName())
          .detail("Reference", r.getReference())
          .cta(token != null ? "Set your password" : "Open your portal", url)
          .closing(
              token != null
                  ? "The link works for 72 hours. If it expires, use \"Forgot password\" on the"
                      + " sign-in page with this email address."
                  : "Sign in with your existing password.")
          .closing("If the button does not work, copy this link into your browser: " + url);
    }
    if (!sender.send(m)) log.warn("Exhibitor invite for {}: {}", user.getEmail(), url);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUploaded(OrganizationMediaUploadedEvent e) {
    if (!sender.configured() || notifyEmail == null || notifyEmail.isBlank()) return;
    String url = base() + "/admin/sponsorship-reservations?tab=uploads";
    sender.send(
        BrandedMail.create()
            .to(notifyEmail)
            .subject("Exhibitor upload awaiting review: " + e.organizationName())
            .preheader(e.count() + " new file(s) from " + e.organizationName())
            .title("Exhibitor upload awaiting review")
            .greeting("Hello team,")
            .paragraph(
                e.organizationName()
                    + " uploaded "
                    + e.count()
                    + (e.count() == 1 ? " file" : " files")
                    + " to their exhibitor profile. Nothing shows on the website until you"
                    + " approve it.")
            .detail("Organization", e.organizationName())
            .detail("Uploaded by", e.uploadedBy() == null ? "-" : e.uploadedBy())
            .cta("Review uploads", url));
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReviewed(OrganizationMediaReviewedEvent e) {
    if (!sender.configured() || "PENDING".equals(e.status())) return;
    String to =
        organizations
            .findById(e.organizationId())
            .map(Organization::getContact)
            .map(c -> c.getEmail())
            .orElse(null);
    if (to == null || to.isBlank()) return;
    boolean ok = "APPROVED".equals(e.status());
    String kind = e.mediaKind() == null ? "file" : e.mediaKind().toLowerCase();
    BrandedMail m =
        BrandedMail.create()
            .to(to)
            .subject(
                ok
                    ? "Your " + kind + " is now live – Ethio Build Connect"
                    : "Your " + kind + " needs a change – Ethio Build Connect")
            .title(ok ? "Upload approved" : "Upload not approved")
            .greeting("Hello " + e.organizationName() + ",")
            .paragraph(
                ok
                    ? "Our team approved the "
                        + kind
                        + (e.fileName() != null ? " \"" + e.fileName() + "\"" : "")
                        + " you uploaded. It now shows on your exhibitor profile."
                    : "Our team could not approve the "
                        + kind
                        + (e.fileName() != null ? " \"" + e.fileName() + "\"" : "")
                        + " you uploaded. Please replace it from your exhibitor portal.");
    if (!ok && e.note() != null) m.message("Reason", e.note());
    m.cta("Open your portal", base() + "/exhibitor");
    sender.send(m);
  }
}
