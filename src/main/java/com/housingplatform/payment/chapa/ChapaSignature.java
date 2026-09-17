package com.housingplatform.payment.chapa;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chapa signs webhooks with HMAC-SHA256 and the account's webhook secret. Two header variants exist
 * in the wild ({@code Chapa-Signature} over the payload, {@code x-chapa-signature} over the secret
 * itself), so both are accepted. The signature only gates the request: the payment state is always
 * confirmed through the verify API afterwards.
 */
public final class ChapaSignature {
  private ChapaSignature() {}

  public static String hmacSha256Hex(String secret, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }

  public static boolean matches(
      String secret, String payload, String chapaSignature, String xChapaSignature) {
    if (secret == null || secret.isBlank()) {
      return false;
    }
    String overPayload = hmacSha256Hex(secret, payload);
    String overSecret = hmacSha256Hex(secret, secret);
    return equalsConstantTime(overPayload, chapaSignature)
        || equalsConstantTime(overPayload, xChapaSignature)
        || equalsConstantTime(overSecret, chapaSignature)
        || equalsConstantTime(overSecret, xChapaSignature);
  }

  private static boolean equalsConstantTime(String expected, String provided) {
    if (provided == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        provided.trim().getBytes(StandardCharsets.UTF_8));
  }
}
