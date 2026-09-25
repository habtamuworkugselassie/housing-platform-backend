package com.housingplatform.payment.chapa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChapaRestClientTest {

  @Test
  void readsChapasErrorMessage() {
    assertThat(
            ChapaRestClient.providerMessage(
                "{\"data\":null,\"message\":\"Callback URL must start with https:// and be a valid"
                    + " URL\",\"status\":\"failed\"}"))
        .isEqualTo("Callback URL must start with https:// and be a valid URL");
    assertThat(ChapaRestClient.providerMessage("<html>bad gateway</html>")).isNull();
    assertThat(ChapaRestClient.providerMessage(null)).isNull();
  }
}
