package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.VerificationService;
import com.housingplatform.identity.service.otp.OtpStore;
import com.housingplatform.identity.service.otp.OtpStore.PendingOtp;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VerificationServiceImpl implements VerificationService {

  /** How long an issued code stays valid. */
  static final Duration OTP_TTL = Duration.ofMinutes(5);

  /** Wrong guesses allowed before the code is burned and a new one must be requested. */
  static final int MAX_ATTEMPTS = 5;

  /** Minimum gap between two codes sent to the same number. */
  static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

  private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final UserRepository userRepository;
  private final BrandedMailSender mailSender;
  private final SmsSender smsSender;
  private final OtpStore store;
  private final SecureRandom secureRandom = new SecureRandom();
  private Clock clock = Clock.systemUTC();

  /** Codes one number may request per UTC day, across all channels. */
  @Value("${otp.max-sends-per-phone-per-day:8}")
  private int maxSendsPerPhonePerDay = 8;

  /**
   * Ceiling on SMS sent per hour across the whole site. SMS costs money per message, so this is the
   * backstop against SMS-pumping fraud: above it, codes still go out by email only.
   */
  @Value("${otp.sms.max-per-hour:60}")
  private int maxSmsPerHour = 60;

  /** Calling codes SMS may be sent to (comma separated, digits only). */
  @Value("${otp.sms.allowed-country-codes:251}")
  private String smsAllowedCountryCodes = "251";

  @Value("${twilio.account-sid:}")
  private String twilioAccountSid;

  @Value("${twilio.auth-token:}")
  private String twilioAuthToken;

  @Value("${twilio.whatsapp-from-number:}")
  private String twilioWhatsappFromNumber;

  public VerificationServiceImpl(
      UserRepository userRepository,
      BrandedMailSender mailSender,
      SmsSender smsSender,
      OtpStore store) {
    this.userRepository = userRepository;
    this.mailSender = mailSender;
    this.smsSender = smsSender;
    this.store = store;
  }

  /** Test hook. */
  void setClock(Clock clock) {
    this.clock = clock;
  }

  /** Test hook. */
  void setLimits(int perPhonePerDay, int smsPerHour, String smsCountryCodes) {
    this.maxSendsPerPhonePerDay = perPhonePerDay;
    this.maxSmsPerHour = smsPerHour;
    this.smsAllowedCountryCodes = smsCountryCodes;
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

    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    if (!store.tryAcquire("cooldown:" + phoneNumber, RESEND_COOLDOWN)) {
      throw new BusinessException(
          "A verification code was sent recently. Please wait a minute before requesting another.");
    }
    long today = store.increment("day:" + now.format(DAY) + ":" + phoneNumber, Duration.ofDays(1));
    if (today > maxSendsPerPhonePerDay) {
      log.warn("OTP daily limit reached for {}", phoneNumber);
      throw new BusinessException(
          "Too many codes were requested for this number today. Please try again tomorrow.");
    }

    User user = userRepository.findByPhoneNumber(phoneNumber).orElse(null);
    if (user == null) {
      // Codes only ever go to registered numbers: sending to arbitrary numbers would let anyone
      // spend our SMS credits (SMS pumping). Answer as if it went out so the endpoint cannot be
      // used to discover which numbers are registered.
      log.info("OTP requested for unregistered phone {} - not sent", phoneNumber);
      return;
    }

    String otp = generate6DigitOtp();

    boolean smsAttempted = smsAllowed(phoneNumber, now);
    boolean smsSent = smsAttempted && smsSender.send(phoneNumber, smsText(otp));

    boolean whatsappAttempted = whatsappConfigured();
    boolean whatsappSent = whatsappAttempted && sendWhatsApp(phoneNumber, otp);

    String email = user.getEmail();
    boolean emailAttempted = email != null && !email.isBlank() && mailSender.configured();
    boolean emailSent = emailAttempted && sendEmail(user, otp, lang);

    if (smsSent || whatsappSent || emailSent) {
      store.save(phoneNumber, new PendingOtp(otp, smsSent || whatsappSent, emailSent, 0), OTP_TTL);
      return;
    }
    if (smsAttempted || whatsappAttempted || emailAttempted) {
      throw new BusinessException("Failed to send verification code. Please try again later.");
    }
    // Local development with no channel configured. Keep the code usable, but only write it at
    // DEBUG so a misconfigured production instance does not leak live login codes into its logs.
    store.save(phoneNumber, new PendingOtp(otp, false, false, 0), OTP_TTL);
    log.warn("No OTP delivery channel configured - code for {} was not sent", phoneNumber);
    log.debug("OTP MOCK DISPATCH to {}: verification code {}", phoneNumber, otp);
  }

  @Override
  public OtpVerification verify(String phoneNumber, String code) {
    if (phoneNumber == null || code == null) {
      return OtpVerification.INVALID;
    }
    PendingOtp pending = store.find(phoneNumber).orElse(null);
    if (pending == null) {
      return OtpVerification.INVALID;
    }

    boolean matches =
        MessageDigest.isEqual(
            pending.code().getBytes(StandardCharsets.UTF_8),
            code.trim().getBytes(StandardCharsets.UTF_8));
    if (matches) {
      // Single use: only the request that actually removes the code wins.
      return store.delete(phoneNumber)
          ? new OtpVerification(true, pending.reachedPhone(), pending.reachedEmail())
          : OtpVerification.INVALID;
    }

    int failed = store.recordFailure(phoneNumber);
    if (failed >= MAX_ATTEMPTS) {
      // Burn the code: the caller has to request a fresh one, which is throttled.
      store.delete(phoneNumber);
      log.warn("OTP for {} invalidated after {} failed attempts", phoneNumber, MAX_ATTEMPTS);
    }
    return OtpVerification.INVALID;
  }

  /** SMS only to allowed countries and below the site-wide hourly ceiling. */
  private boolean smsAllowed(String phoneNumber, LocalDateTime now) {
    if (!smsSender.configured()) {
      return false;
    }
    String digits = phoneNumber.replaceAll("[^0-9]", "");
    List<String> allowed =
        Arrays.stream(smsAllowedCountryCodes.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    if (allowed.stream().noneMatch(digits::startsWith)) {
      log.info("SMS not sent to {}: country not in otp.sms.allowed-country-codes", phoneNumber);
      return false;
    }
    long thisHour = store.increment("sms:hour:" + now.format(HOUR), Duration.ofHours(1));
    if (thisHour > maxSmsPerHour) {
      log.error(
          "SMS hourly ceiling ({}) reached - possible SMS pumping. Codes go by email only.",
          maxSmsPerHour);
      return false;
    }
    return true;
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
            .paragraph(am ? "ለመግባት ይህን ኮድ ያስገቡ፦" : "Enter this code to sign in:")
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

  private String generate6DigitOtp() {
    int number = secureRandom.nextInt(1_000_000);
    return String.format("%06d", number);
  }
}
