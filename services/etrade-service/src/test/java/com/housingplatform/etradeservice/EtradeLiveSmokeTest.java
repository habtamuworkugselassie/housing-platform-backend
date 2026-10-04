package com.housingplatform.etradeservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.etradeservice.client.EtradeRegistrationResponse;
import com.housingplatform.etradeservice.client.EtradeRestClient;
import com.housingplatform.etradeservice.config.EtradeProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.client.RestClient;

/**
 * Talks to the real etrade.gov.et. Off by default (network, slow, flaky upstream); run with {@code
 * ETRADE_LIVE_TEST=true mvn -Dtest=EtradeLiveSmokeTest test}. Proves the TLS trust fix and the
 * Referer handling against the production portal.
 */
@EnabledIfEnvironmentVariable(named = "ETRADE_LIVE_TEST", matches = "true")
class EtradeLiveSmokeTest {

  @Test
  void fetchesTheSampleTinFromTheBusinessLicenseChecker() {
    EtradeRestClient client =
        new EtradeRestClient(
            new EtradeProperties(),
            RestClient.builder(),
            new ObjectMapper(),
            new DefaultResourceLoader());

    Optional<EtradeRegistrationResponse> result = client.findRegistrationByTin("0081723672");

    assertThat(result).isPresent();
    assertThat(result.get().tin()).isEqualTo("0081723672");
    assertThat(result.get().businessName()).isNotBlank();
    assertThat(result.get().registrationNumber()).isNotBlank();
  }

  /** Many all-zero style TINs (0000000000, 0000000123) are real placeholder records upstream. */
  @Test
  void anUnregisteredTinIsEmpty() {
    EtradeRestClient client =
        new EtradeRestClient(
            new EtradeProperties(),
            RestClient.builder(),
            new ObjectMapper(),
            new DefaultResourceLoader());
    assertThat(client.findRegistrationByTin("0081723673")).isEmpty();
  }
}
