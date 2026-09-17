package com.housingplatform.payment.chapa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChapaSignatureTest {

  private static final String SECRET = "whsec_test";
  private static final String PAYLOAD = "{\"tx_ref\":\"PPO-1-DEP1-ABC\",\"status\":\"success\"}";

  @Test
  void acceptsAPayloadSignatureInEitherHeader() {
    String sig = ChapaSignature.hmacSha256Hex(SECRET, PAYLOAD);
    assertThat(ChapaSignature.matches(SECRET, PAYLOAD, sig, null)).isTrue();
    assertThat(ChapaSignature.matches(SECRET, PAYLOAD, null, sig)).isTrue();
    assertThat(ChapaSignature.matches(SECRET, PAYLOAD, " " + sig + " ", null)).isTrue();
  }

  @Test
  void acceptsTheSecretOverSecretVariant() {
    String sig = ChapaSignature.hmacSha256Hex(SECRET, SECRET);
    assertThat(ChapaSignature.matches(SECRET, PAYLOAD, null, sig)).isTrue();
  }

  @Test
  void rejectsTamperedPayloadsMissingHeadersAndAnUnconfiguredSecret() {
    String sig = ChapaSignature.hmacSha256Hex(SECRET, PAYLOAD);
    assertThat(ChapaSignature.matches(SECRET, PAYLOAD + " ", sig, null)).isFalse();
    assertThat(ChapaSignature.matches(SECRET, PAYLOAD, "deadbeef", null)).isFalse();
    assertThat(ChapaSignature.matches(SECRET, PAYLOAD, null, null)).isFalse();
    assertThat(ChapaSignature.matches("", PAYLOAD, sig, null)).isFalse();
  }

  @Test
  void hmacIsDeterministicHex() {
    assertThat(ChapaSignature.hmacSha256Hex("key", "The quick brown fox jumps over the lazy dog"))
        .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
  }
}
