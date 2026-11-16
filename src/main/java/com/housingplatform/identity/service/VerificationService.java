package com.housingplatform.identity.service;

public interface VerificationService {

  /**
   * What a correct code proves. A code that reached the phone (SMS / WhatsApp) proves the phone
   * number; one that only reached the inbox proves the email address and nothing about the phone.
   */
  record OtpVerification(boolean valid, boolean phoneProven, boolean emailProven) {
    public static final OtpVerification INVALID = new OtpVerification(false, false, false);
  }

  /**
   * Generates a secure 6-digit OTP for the phone number and delivers it over every configured
   * channel: SMS (Brevo), WhatsApp (Twilio) and email to the account registered with that number.
   *
   * @param lang "am" for Amharic copy, anything else for English
   */
  void sendOtp(String phoneNumber, String lang);

  /** Checks and consumes the code. */
  OtpVerification verify(String phoneNumber, String code);
}
