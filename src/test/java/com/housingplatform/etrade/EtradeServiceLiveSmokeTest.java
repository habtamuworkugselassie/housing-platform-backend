package com.housingplatform.etrade;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.housingplatform.etrade.client.EtradeServiceRestClient;
import com.housingplatform.etrade.dto.EtradeBusinessDetails;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Talks to a running etrade-service (which in turn talks to etrade.gov.et). Off by default; run
 * with {@code ETRADE_SERVICE_LIVE_TEST=true ETRADE_SERVICE_URL=http://localhost:8085
 * ETRADE_SERVICE_API_KEY=... mvn -Dtest=EtradeServiceLiveSmokeTest test}.
 */
@EnabledIfEnvironmentVariable(named = "ETRADE_SERVICE_LIVE_TEST", matches = "true")
class EtradeServiceLiveSmokeTest {

  private static EtradeServiceRestClient client() {
    EtradeProperties properties = new EtradeProperties();
    properties.setServiceUrl(
        System.getenv().getOrDefault("ETRADE_SERVICE_URL", "http://localhost:8085"));
    properties.setServiceApiKey(System.getenv().getOrDefault("ETRADE_SERVICE_API_KEY", ""));
    return new EtradeServiceRestClient(
        properties, RestClient.builder(), new ObjectMapper().registerModule(new JavaTimeModule()));
  }

  @Test
  void fetchesTheSampleTinThroughTheService() {
    Optional<EtradeBusinessDetails> result = client().findBusiness("0081723672", false);
    assertThat(result).isPresent();
    assertThat(result.get().businessName()).isNotBlank();
    assertThat(result.get().registrationNumber()).isNotBlank();
    assertThat(result.get().businesses()).isNotEmpty();
  }

  @Test
  void unknownTinIsEmpty() {
    assertThat(client().findBusiness("0081723673", false)).isEmpty();
  }
}
