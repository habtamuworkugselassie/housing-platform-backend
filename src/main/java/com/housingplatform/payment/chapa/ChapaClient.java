package com.housingplatform.payment.chapa;

import java.math.BigDecimal;

/** The two Chapa calls the platform needs. Implemented over HTTP; mocked in tests. */
public interface ChapaClient {

  record InitializeRequest(
      BigDecimal amount,
      String currency,
      String txRef,
      String email,
      String firstName,
      String lastName,
      String phoneNumber,
      String callbackUrl,
      String returnUrl,
      String title,
      String description) {}

  record InitializeResult(String checkoutUrl) {}

  /** What Chapa says about a transaction; {@code status} is "success", "pending" or "failed". */
  record VerifyResult(
      String status,
      BigDecimal amount,
      String currency,
      String reference,
      String txRef,
      String method,
      String type,
      BigDecimal charge) {
    public boolean isSuccess() {
      return "success".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
      return "failed".equalsIgnoreCase(status);
    }
  }

  boolean isConfigured();

  InitializeResult initialize(InitializeRequest request);

  VerifyResult verify(String txRef);
}
