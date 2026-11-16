package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.VerificationService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import com.housingplatform.shared.mail.MailLocale;
import com.housingplatform.shared.sms.SmsSender;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

  /** How long an issued code stays valid. */
  static final Duration OTP_TTL = Duration.ofMinutes(5);

  /** Wrong guesses allowed before the code is burned and a new one must be requested. */
  static final int MAX_ATTEMPTS = 5;

  /** Minimum gap between two codes sent to the same number. */
  static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

  /**
   * A single outstanding code for a phone number. Kept in memory: codes are short-lived, and a
   * restart simply forces users to request a new one. Move to Redis when running more than one
   * backend instance.
   */
  private record PendingOtp(String code, Instant issuedAt, Instant expiresAt, int failedAttempts) {
    PendingOtp withFailure() {
      return new PendingOtp(code, issuedAt, expiresAt, failedAttempts + 1);
    }
  }

  private final UserRepository userRepository;
  private final BrandedMailSender mailSender;
  private final SmsSender smsSender;

  private final Map<String, PendingOtp> otpStorage = new ConcurrentHashMap<>();
  private final SecureRandom secureRandom = new SecureRandom();
  private Clock clock = Clock.systemUTC();

  @Value("${twilio.account-sid:}")
  private String twilioAccountSid;

  @Value("${twilio.auth-token:}")
  private String twilioAuthToken;

  @Value("${twilio.whatsapp-from-number:}")
  private String twilioWhatsappFromNumber;

  /** Test hook. */
  void setClock(Clock clock) {
    this.clock = clock;
  }

  @PostConstruct
  public void initTwilio() {
    if (twilioAccountSid != null
        && !twilioAccountSid.isBlank()
        && twilioAuthToken != null
        && !twilioAuthToken.isBlank()) {
      Twilio.init(twilioAccountSid, twilioAuthToken);
      log.info("Twilio initialized for WhatsApp messaging.");
    } else {
      log.info("Twilio credentials not provided. WhatsApp OTP delivery is off.");
    }
  }

  @Override
  public void sendOtp(String phoneNumber, String lang) {
    if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
      throw new BusinessException("Phone number Cannot be empty");
    }

    Instant now = clock.instant();
    PendingOtp existing = otpStorage.get(phoneNumber);
    if (existing != null
        && now.isBefore(existing.expiresAt())
        && now.isBefore(existing.issuedAt().plus(RESEND_COOLDOWN))) {
      throw new BusinessException(
          "A verification code was sent recently. Please wait a minute before requesting another.");
    }

    String otp = generate6DigitOtp();
    otpStorage.put(phoneNumber, new PendingOtp(otp, now, now.plus(OTP_TTL), 0));
    purgeExpired(now);

    boolean smsAttempted = smsSender.configured();
    boolean smsSent = smsAttempted && smsSender.send(phoneNumber, smsText(otp));

    boolean whatsappAttempted = whatsappConfigured();
    boolean whatsappSent = whatsappAttempted && sendWhatsApp(phoneNumber, otp);

    User user = userRepository.findByPhoneNumber(phoneNumber).orElse(null);
    String email = user == null ? null : user.getEmail();
    boolean emailAttempted = email != null && !email.isBlank() && mailSender.configured();
    boolean emailSent = emailAttempted && sendEmail(user, otp, lang);

    if (smsSent || whatsappSent || emailSent) {
      return;
    }
    if (smsAttempted || whatsappAttempted || emailAttempted) {
      // A channel exists but delivery failed: let the caller retry instead of waiting for nothing.
      otpStorage.remove(phoneNumber);
      throw new BusinessException("Failed to send verification code. Please try again later.");
    }
    if (user == null) {
      // No account for this number and no SMS/WhatsApp: there is nowhere to send a code. Answer as
      // if
      // it went out so the endpoint cannot be used to discover which numbers are registered.
      log.info("OTP requested for unregistered phone {} - no delivery channel", phoneNumber);
      return;
    }
    // Local development with neither Twilio nor mail configured. The code is only written at
    // DEBUG so a misconfigured production instance does not leak live login codes into its logs.
    log.warn("No OTP delivery channel configured - code for {} was not sent", phoneNumber);
    log.debug("OTP MOCK DISPATCH to {}: verification code {}", phoneNumber, otp);
  }

  @Override
  public boolean verifyOtp(String phoneNumber, String code) {
    if (phoneNumber == null || code == null) {
      return false;
    }

    PendingOtp pending = otpStorage.get(phoneNumber);
    if (pending == null) {
      return false;
    }
    if (!clock.instant().isBefore(pending.expiresAt())) {
      otpStorage.remove(phoneNumber, pending);
      return false;
    }

    boolean matches =
        MessageDigest.isEqual(
            pending.code().getBytes(StandardCharsets.UTF_8),
            code.trim().getBytes(StandardCharsets.UTF_8));
    if (matches) {
      // Consume the OTP so it cannot be reused
      otpStorage.remove(phoneNumber, pending);
      return true;
    }

    PendingOtp failed = pending.withFailure();
    if (failed.failedAttempts() >= MAX_ATTEMPTS) {
      // Burn the code: the caller has to request a fresh one, which is subject to the cooldown.
      otpStorage.remove(phoneNumber, pending);
      log.warn("OTP for {} invalidated after {} failed attempts", phoneNumber, MAX_ATTEMPTS);
    } else {
      otpStorage.replace(phoneNumber, pending, failed);
    }
    return false;
  }

  /**
   * Kept ASCII and short on purpose: Amharic would switch the SMS to Unicode, which halves the
   * characters per message and doubles the cost. The bilingual wording lives in the email.
   */
  static String smsText(String otp) {
    return "Ethio Build Connect code: "
        + otp
        + ". Valid "
        + OTP_TTL.toMinutes()
        + " min. Do not share it with anyone.";
  }

  private boolean whatsappConfigured() {
    return twilioAccountSid != null
        && !twilioAccountSid.isBlank()
        && twilioWhatsappFromNumber != null
        && !twilioWhatsappFromNumber.isBlank();
  }

  private boolean sendWhatsApp(String phoneNumber, String otp) {
    try {
      Message.creator(
              new com.twilio.type.PhoneNumber("whatsapp:" + phoneNumber),
              new com.twilio.type.PhoneNumber("whatsapp:" + twilioWhatsappFromNumber),
              "Your Ethio Build Connect verification code is: " + otp)
          .create();
      log.info("WhatsApp OTP dispatched to {}", phoneNumber);
      return true;
    } catch (Exception e) {
      log.error("Failed to send WhatsApp OTP via Twilio", e);
      return false;
    }
  }

  private boolean sendEmail(User user, String otp, String lang) {
    boolean am = MailLocale.isAmharic(lang);
    long minutes = OTP_TTL.toMinutes();
    String name = user.getFirstName() == null ? "" : user.getFirstName().trim();
    String greeting;
    if (name.isEmpty()) {
      greeting = am ? "ሰላም፣" : "Hello,";
    } else {
      greeting = (am ? "ሰላም " : "Hello ") + name + ",";
    }
    BrandedMail mail =
        BrandedMail.create()
            .lang(lang)
            .to(user.getEmail())
            .subject(
                am
                    ? "የEthio Build Connect ማረጋገጫ ኮድዎ፦ " + otp
                    : "Your Ethio Build Connect verification code: " + otp)
            .preheader(
                am ? "ኮዱ ለ" + minutes + " ደቂቃ ያገለግላል።" : "Valid for " + minutes + " minutes.")
            .title(am ? "የማረጋገጫ ኮድ" : "Verification code")
            .greeting(greeting)
            .paragraph(
                am
                    ? "ለመግባት ወይም ስልክዎን ለማረጋገጥ ይህን ኮድ ያስገቡ፦"
                    : "Enter this code to sign in or verify your phone number:")
            .detail(am ? "ኮድ" : "Code", otp)
            .paragraph(
                am
                    ? "ኮዱ ለ" + minutes + " ደቂቃ ብቻ ያገለግላል። ለማንም አያጋሩት።"
                    : "The code expires in " + minutes + " minutes. Never share it with anyone.")
            .paragraph(
                am
                    ? "ይህን ኮድ እርስዎ ካልጠየቁ ይህን ኢሜይል ችላ ይበሉ።"
                    : "If you did not request this code, you can ignore this email.");
    boolean sent = mailSender.send(mail);
    if (sent) {
      log.info("Email OTP dispatched for user {}", user.getId());
    }
    return sent;
  }

  private void purgeExpired(Instant now) {
    otpStorage.values().removeIf(p -> !now.isBefore(p.expiresAt()));
  }

  private String generate6DigitOtp() {
    int number = secureRandom.nextInt(1_000_000);
    return String.format("%06d", number);
  }
}
