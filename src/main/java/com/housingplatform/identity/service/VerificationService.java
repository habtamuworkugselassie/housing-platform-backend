package com.housingplatform.identity.service;

public interface VerificationService {

  /**
   * Generates a secure 6-digit OTP for the phone number and delivers it over every configured
   * channel: SMS (Brevo), WhatsApp (Twilio) and email to the account registered with that number.
   *
   * @param lang "am" for Amharic copy, anything else for English
   */
  void sendOtp(String phoneNumber, String lang);

  /** Validates the provided OTP code against the stored value. */
  boolean verifyOtp(String phoneNumber, String code);
}
