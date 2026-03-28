package com.housingplatform.identity.service;

public interface VerificationService {

  /** Generates a secure 6-digit OTP and dispatches it via WhatsApp. */
  void sendWhatsAppOtp(String phoneNumber);

  /** Validates the provided OTP code against the stored value. */
  boolean verifyOtp(String phoneNumber, String code);
}
