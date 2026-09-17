package com.housingplatform.payment.chapa;

import com.housingplatform.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Chapa's REST API: {@code POST /transaction/initialize} returns a hosted checkout URL; {@code GET
 * /transaction/verify/{tx_ref}} is the source of truth for whether a payment went through.
 */
@Component
@Slf4j
public class ChapaRestClient implements ChapaClient {

  private final ChapaProperties properties;
  private final RestClient restClient;

  public ChapaRestClient(ChapaProperties properties, RestClient.Builder builder) {
    this.properties = properties;
    this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
  }

  @Override
  public boolean isConfigured() {
    return properties.isConfigured();
  }

  @Override
  @SuppressWarnings("unchecked")
  public InitializeResult initialize(InitializeRequest request) {
    requireConfigured();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("amount", request.amount().toPlainString());
    body.put("currency", request.currency());
    body.put("tx_ref", request.txRef());
    if (request.email() != null && !request.email().isBlank()) {
      body.put("email", request.email());
    }
    body.put("first_name", nz(request.firstName(), "Buyer"));
    body.put("last_name", nz(request.lastName(), "-"));
    if (request.phoneNumber() != null) {
      body.put("phone_number", request.phoneNumber());
    }
    if (request.callbackUrl() != null) {
      body.put("callback_url", request.callbackUrl());
    }
    body.put("return_url", request.returnUrl());
    Map<String, Object> customization = new LinkedHashMap<>();
    // Chapa caps the title at 16 characters.
    customization.put("title", truncate(request.title(), 16));
    customization.put("description", truncate(request.description(), 100));
    body.put("customization", customization);

    Map<String, Object> response;
    try {
      response =
          restClient
              .post()
              .uri("/transaction/initialize")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getSecretKey())
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(Map.class);
    } catch (RestClientException e) {
      log.error("Chapa initialize failed for {}", request.txRef(), e);
      throw new BusinessException(
          "The payment provider could not start the checkout. Please try again.");
    }
    Object data = response != null ? response.get("data") : null;
    Object url = data instanceof Map<?, ?> m ? m.get("checkout_url") : null;
    if (!"success".equals(String.valueOf(response != null ? response.get("status") : null))
        || url == null) {
      log.error("Chapa initialize returned {}", response);
      throw new BusinessException("The payment provider rejected the checkout request.");
    }
    return new InitializeResult(url.toString());
  }

  @Override
  @SuppressWarnings("unchecked")
  public VerifyResult verify(String txRef) {
    requireConfigured();
    Map<String, Object> response;
    try {
      response =
          restClient
              .get()
              .uri("/transaction/verify/{txRef}", txRef)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getSecretKey())
              .retrieve()
              .body(Map.class);
    } catch (RestClientException e) {
      log.error("Chapa verify failed for {}", txRef, e);
      throw new BusinessException(
          "The payment provider could not confirm the payment yet. Please try again.");
    }
    Object data = response != null ? response.get("data") : null;
    if (!(data instanceof Map<?, ?> d)) {
      return new VerifyResult("pending", null, null, null, txRef, null, null, null);
    }
    return new VerifyResult(
        str(d.get("status")),
        decimal(d.get("amount")),
        str(d.get("currency")),
        str(d.get("reference")),
        str(d.get("tx_ref")),
        str(d.get("method")),
        str(d.get("type")),
        decimal(d.get("charge")));
  }

  private void requireConfigured() {
    if (!properties.isConfigured()) {
      throw new BusinessException("Card payments are not configured on this server");
    }
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  private static BigDecimal decimal(Object o) {
    if (o == null) {
      return null;
    }
    try {
      return new BigDecimal(o.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String nz(String s, String fallback) {
    return s == null || s.isBlank() ? fallback : s;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() <= max ? s : s.substring(0, max);
  }
}
