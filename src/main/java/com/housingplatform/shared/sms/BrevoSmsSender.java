package com.housingplatform.shared.sms;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Transactional SMS through Brevo ({@code POST /v3/transactionalSMS/send}). Off until {@code
 * BREVO_API_KEY} and {@code BREVO_SMS_SENDER} are set. Brevo bills SMS in prepaid credits, and
 * accounts created after March 2026 must register the sender ID for each destination country.
 */
@Slf4j
@Component
public class BrevoSmsSender implements SmsSender {

  /** Brevo's limit for an alphanumeric sender name. */
  static final int MAX_ALPHANUMERIC_SENDER = 11;

  private final String apiKey;
  private final String sender;
  private final RestClient restClient;

  public BrevoSmsSender(
      @Value("${brevo.api-key:}") String apiKey,
      @Value("${brevo.sms.sender:}") String sender,
      @Value("${brevo.base-url:https://api.brevo.com/v3}") String baseUrl,
      RestClient.Builder builder) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.sender = sender == null ? "" : sender.trim();
    this.restClient = builder.baseUrl(baseUrl).build();
    if (this.sender.length() > MAX_ALPHANUMERIC_SENDER && !this.sender.matches("\\d+")) {
      log.warn(
          "BREVO_SMS_SENDER '{}' is longer than {} characters; Brevo will reject it",
          this.sender,
          MAX_ALPHANUMERIC_SENDER);
    }
  }

  @Override
  public boolean configured() {
    return !apiKey.isEmpty() && !sender.isEmpty();
  }

  @Override
  public boolean send(String phoneNumber, String text) {
    if (!configured()) {
      return false;
    }
    String recipient = toBrevoRecipient(phoneNumber);
    if (recipient == null) {
      log.warn("Not sending SMS: '{}' is not a usable phone number", phoneNumber);
      return false;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sender", sender);
    body.put("recipient", recipient);
    body.put("content", text);
    body.put("type", "transactional");
    body.put("tag", "otp");
    try {
      restClient
          .post()
          .uri("/transactionalSMS/send")
          .header("api-key", apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toBodilessEntity();
      log.info("SMS sent via Brevo to {}", maskPhone(recipient));
      return true;
    } catch (RestClientResponseException e) {
      // 400s carry Brevo's reason (unregistered sender, no credits, unsupported country, ...).
      log.error(
          "Brevo SMS to {} failed: HTTP {} {}",
          maskPhone(recipient),
          e.getStatusCode().value(),
          e.getResponseBodyAsString());
      return false;
    } catch (Exception e) {
      log.error("Brevo SMS to {} failed", maskPhone(recipient), e);
      return false;
    }
  }

  /** Brevo wants the full international number as digits only, without the leading '+'. */
  static String toBrevoRecipient(String phoneNumber) {
    if (phoneNumber == null) {
      return null;
    }
    String digits = phoneNumber.replaceAll("[^0-9]", "");
    if (phoneNumber.trim().startsWith("00")) {
      digits = digits.substring(2);
    }
    return digits.length() >= 8 && digits.length() <= 15 ? digits : null;
  }

  private static String maskPhone(String digits) {
    return digits.length() <= 4 ? "****" : "****" + digits.substring(digits.length() - 4);
  }
}
