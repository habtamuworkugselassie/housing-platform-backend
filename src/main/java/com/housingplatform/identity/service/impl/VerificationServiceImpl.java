package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.service.VerificationService;
import com.housingplatform.shared.exception.BusinessException;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
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

  // For production, store this in Redis with a TTL of ~5 minutes.
  // Using an in-memory map for the standalone local environment.
  private final Map<String, String> otpStorage = new ConcurrentHashMap<>();
  private final SecureRandom secureRandom = new SecureRandom();

  @Value("${twilio.account-sid:}")
  private String twilioAccountSid;

  @Value("${twilio.auth-token:}")
  private String twilioAuthToken;

  @Value("${twilio.whatsapp-from-number:}")
  private String twilioWhatsappFromNumber;

  @PostConstruct
  public void initTwilio() {
    if (twilioAccountSid != null
        && !twilioAccountSid.isBlank()
        && twilioAuthToken != null
        && !twilioAuthToken.isBlank()) {
      Twilio.init(twilioAccountSid, twilioAuthToken);
      log.info("Twilio initialized for WhatsApp messaging.");
    } else {
      log.warn("Twilio credentials not fully provided. WhatsApp mock dispatch will be used.");
    }
  }

  @Override
  public void sendWhatsAppOtp(String phoneNumber) {
    if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
      throw new BusinessException("Phone number Cannot be empty");
    }

    String otp = generate6DigitOtp();

    // Store the OTP
    otpStorage.put(phoneNumber, otp);

    if (twilioAccountSid != null && !twilioAccountSid.isBlank()) {
      try {
        Message.creator(
                new com.twilio.type.PhoneNumber("whatsapp:" + phoneNumber),
                new com.twilio.type.PhoneNumber("whatsapp:" + twilioWhatsappFromNumber),
                "Your Ethio Build Connect verification code is: " + otp)
            .create();
        log.info("Real WhatsApp message dispatched to {}", phoneNumber);
      } catch (Exception e) {
        log.error("Failed to send WhatsApp message via Twilio", e);
        throw new BusinessException("Failed to send verification code. Please try again later.");
      }
    } else {
      // Mock dispatch fallback
      log.info("================================================");
      log.info("WHATSAPP MOCK DISPATCH");
      log.info("TO: {}", phoneNumber);
      log.info("MESSAGE: Your Ethio Build Connect verification code is: {}", otp);
      log.info("================================================");
    }
  }

  @Override
  public boolean verifyOtp(String phoneNumber, String code) {
    if (phoneNumber == null || code == null) {
      return false;
    }

    String storedOtp = otpStorage.get(phoneNumber);
    if (storedOtp != null && storedOtp.equals(code)) {
      // Consume the OTP so it cannot be reused
      otpStorage.remove(phoneNumber);
      return true;
    }
    return false;
  }

  private String generate6DigitOtp() {
    int number = secureRandom.nextInt(999999);
    return String.format("%06d", number);
  }
}
