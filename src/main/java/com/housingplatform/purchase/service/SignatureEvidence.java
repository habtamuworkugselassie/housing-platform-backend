package com.housingplatform.purchase.service;

/** Where a signature came from, captured by the controller from the HTTP request. */
public record SignatureEvidence(String ipAddress, String userAgent) {
  public static SignatureEvidence none() {
    return new SignatureEvidence(null, null);
  }
}
