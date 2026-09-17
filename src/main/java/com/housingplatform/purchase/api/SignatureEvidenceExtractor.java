package com.housingplatform.purchase.api;

import com.housingplatform.purchase.service.SignatureEvidence;
import jakarta.servlet.http.HttpServletRequest;

/** Captures the client's address and user agent as evidence attached to a signature. */
final class SignatureEvidenceExtractor {
  private SignatureEvidenceExtractor() {}

  static SignatureEvidence from(HttpServletRequest request) {
    if (request == null) {
      return SignatureEvidence.none();
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    String ip =
        forwarded != null && !forwarded.isBlank()
            ? forwarded.split(",")[0].trim()
            : request.getRemoteAddr();
    return new SignatureEvidence(ip, request.getHeader("User-Agent"));
  }
}
