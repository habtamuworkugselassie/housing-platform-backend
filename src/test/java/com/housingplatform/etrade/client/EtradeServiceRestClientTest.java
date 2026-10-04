package com.housingplatform.etrade.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.housingplatform.etrade.EtradeProperties;
import com.housingplatform.etrade.EtradeUnavailableException;
import com.housingplatform.etrade.dto.EtradeBusinessDetails;
import com.housingplatform.shared.exception.BusinessException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EtradeServiceRestClientTest {

  private static final String URL = "http://etrade-service:8085/api/v1/businesses/0081723672";

  /** What the standalone service returns (see services/etrade-service). */
  private static final String BODY =
      """
      {"tin":"0081723672","businessName":"FIREW WONDE BELAY",
       "legalCondition":{"code":"1","name":"Sole Proprietorship","nameAmharic":"ግለሰብ ነጋዴ"},
       "registrationNumber":"AA/LK/14/1/3631426/2016","registrationDate":"2022-12-01",
       "paidUpCapital":500.0,"associates":[{"name":"FIREW WONDE"}],
       "businesses":[{"id":"g","tradeName":"FIREW WONDE BELAY","licenseNumber":"YK/AA/14/672/5990336/2016",
         "renewedFrom":"2026-07-08","renewedTo":"2027-07-07","licenseStatus":"ACTIVE",
         "sectors":[{"code":62222,"description":"Retail trade in beverage products"}]}],
       "hasActiveLicense":true,"fetchedAt":"2026-10-04T11:05:40Z",
       "sourceUrl":"https://etrade.gov.et/business-license-checker?tin=0081723672"}
      """;

  private MockRestServiceServer server;

  private EtradeServiceRestClient client(String apiKey) {
    EtradeProperties properties = new EtradeProperties();
    properties.setServiceUrl("http://etrade-service:8085/");
    properties.setServiceApiKey(apiKey);
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    return EtradeServiceRestClient.forTesting(
        properties, builder, new ObjectMapper().registerModule(new JavaTimeModule()));
  }

  @Test
  void sendsTheApiKeyAndDeserializesTheServiceRecord() {
    EtradeServiceRestClient client = client("secret-1");
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-API-Key", "secret-1"))
        .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

    Optional<EtradeBusinessDetails> result = client.findBusiness("0081723672", false);

    assertThat(result).isPresent();
    EtradeBusinessDetails d = result.get();
    assertThat(d.businessName()).isEqualTo("FIREW WONDE BELAY");
    assertThat(d.legalCondition().name()).isEqualTo("Sole Proprietorship");
    assertThat(d.registrationDate()).isEqualTo(LocalDate.of(2022, 12, 1));
    assertThat(d.businesses()).hasSize(1);
    assertThat(d.businesses().get(0).licenseStatus())
        .isEqualTo(EtradeBusinessDetails.LicenseStatus.ACTIVE);
    assertThat(d.businesses().get(0).sectors().get(0).code()).isEqualTo(62222);
    assertThat(d.hasActiveLicense()).isTrue();
    server.verify();
  }

  @Test
  void photosFlagBecomesAQueryParameterAndNoKeyMeansNoHeader() {
    EtradeServiceRestClient client = client("");
    server
        .expect(requestTo(URL + "?includePhotos=true"))
        .andExpect(headerDoesNotExist("X-API-Key"))
        .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
    assertThat(client.findBusiness("0081723672", true)).isPresent();
    server.verify();
  }

  @Test
  void serviceNotFoundIsEmpty() {
    EtradeServiceRestClient client = client("k");
    server
        .expect(requestTo(URL))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":404,\"message\":\"No business is registered on eTrade\"}"));
    assertThat(client.findBusiness("0081723672", false)).isEmpty();
  }

  @Test
  void serviceBadRequestSurfacesItsMessageAsABusinessError() {
    EtradeServiceRestClient client = client("k");
    server
        .expect(requestTo(URL))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":400,\"message\":\"TIN must be exactly 10 digits\"}"));
    assertThatThrownBy(() -> client.findBusiness("0081723672", false))
        .isInstanceOf(BusinessException.class)
        .hasMessage("TIN must be exactly 10 digits");
  }

  @Test
  void wrongApiKeyIsReportedAsMisconfiguration() {
    EtradeServiceRestClient client = client("k");
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    assertThatThrownBy(() -> client.findBusiness("0081723672", false))
        .isInstanceOf(EtradeUnavailableException.class)
        .hasMessageContaining("API key");
  }

  @Test
  void upstreamFailureKeepsTheServicesMessage() {
    EtradeServiceRestClient client = client("k");
    server
        .expect(requestTo(URL))
        .andRespond(
            withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"status\":502,\"message\":\"eTrade (etrade.gov.et) could not be reached\"}"));
    assertThatThrownBy(() -> client.findBusiness("0081723672", false))
        .isInstanceOf(EtradeUnavailableException.class)
        .hasMessage("eTrade (etrade.gov.et) could not be reached");
  }
}
