package com.housingplatform.etradeservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.etradeservice.EtradeUnavailableException;
import com.housingplatform.etradeservice.config.EtradeProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EtradeRestClientTest {

  private static final String URL =
      "https://etrade.gov.et/api/Registration/GetRegistrationInfoByTin/0081723672/en";

  private MockRestServiceServer server;

  private EtradeRestClient client(EtradeProperties properties) {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    return new EtradeRestClient(properties, builder, new ObjectMapper());
  }

  @Test
  void parsesARegistrationRecordAndSendsTheRefererEtradeInsistsOn() throws IOException {
    EtradeRestClient client = client(new EtradeProperties());
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Referer", "https://etrade.gov.et/business-license-checker"))
        .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
        .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

    Optional<EtradeRegistrationResponse> result = client.findRegistrationByTin("0081723672");

    assertThat(result).isPresent();
    EtradeRegistrationResponse r = result.get();
    assertThat(r.tin()).isEqualTo("0081723672");
    assertThat(r.businessName()).isEqualTo("FIREW WONDE BELAY");
    assertThat(r.businessNameAmharic()).isEqualTo("ፍሬው ወንዴ በላይ");
    assertThat(r.legalCondition()).isEqualTo("1");
    assertThat(r.registrationNumber()).isEqualTo("AA/LK/14/1/3631426/2016");
    assertThat(r.registrationDate()).isEqualTo("12/1/2022");
    assertThat(r.paidUpCapital()).isEqualByComparingTo("500.00");
    assertThat(r.associates()).hasSize(1);
    assertThat(r.associates().get(0).managerNameEnglish()).isEqualTo("FIREW WONDE");
    assertThat(r.businesses()).hasSize(1);
    EtradeRegistrationResponse.Business b = r.businesses().get(0);
    assertThat(b.licenceNumber()).isEqualTo("YK/AA/14/672/5990336/2016");
    assertThat(b.renewedFrom()).isEqualTo("7/8/2026");
    assertThat(b.renewedTo()).isEqualTo("7/7/2027");
    assertThat(b.subGroups().get(0).code()).isEqualTo(62222);
    assertThat(b.subGroups().get(0).description()).isEqualTo(" Retail trade in beverage products");
    server.verify();
  }

  @Test
  void unknownTinIsAnEmptyResultNotAnError() {
    EtradeRestClient client = client(new EtradeProperties());
    server.expect(requestTo(URL)).andRespond(withNoContent());
    assertThat(client.findRegistrationByTin("0081723672")).isEmpty();
  }

  @Test
  void languageAndBaseUrlComeFromProperties() {
    EtradeProperties properties = new EtradeProperties();
    properties.setBaseUrl("https://mirror.example.test");
    properties.setLanguage("am");
    EtradeRestClient client = client(properties);
    server
        .expect(
            requestTo(
                "https://mirror.example.test/api/Registration/GetRegistrationInfoByTin/0081723672/am"))
        .andRespond(withNoContent());
    assertThat(client.findRegistrationByTin("0081723672")).isEmpty();
    server.verify();
  }

  @Test
  void aRefererRejectionIsReportedAsAConfigurationProblem() {
    EtradeRestClient client = client(new EtradeProperties());
    server
        .expect(requestTo(URL))
        .andRespond(
            withStatus(HttpStatus.EXPECTATION_FAILED)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Invalid referer headerr"));
    assertThatThrownBy(() -> client.findRegistrationByTin("0081723672"))
        .isInstanceOf(EtradeUnavailableException.class)
        .hasMessageContaining("Referer");
  }

  @Test
  void serverErrorsBecomeEtradeUnavailable() {
    EtradeRestClient client = client(new EtradeProperties());
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
    assertThatThrownBy(() -> client.findRegistrationByTin("0081723672"))
        .isInstanceOf(EtradeUnavailableException.class)
        .hasMessageContaining("502");
  }

  @Test
  void anHtmlErrorPageWithA200IsUnreadableNotANotFound() {
    EtradeRestClient client = client(new EtradeProperties());
    server
        .expect(requestTo(URL))
        .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));
    assertThatThrownBy(() -> client.findRegistrationByTin("0081723672"))
        .isInstanceOf(EtradeUnavailableException.class)
        .hasMessageContaining("unreadable");
  }

  @Test
  void anEmpty200IsTreatedAsNotFound() {
    EtradeRestClient client = client(new EtradeProperties());
    server.expect(requestTo(URL)).andRespond(withSuccess());
    assertThat(client.findRegistrationByTin("0081723672")).isEmpty();
  }

  private static String fixture() throws IOException {
    return new ClassPathResource("etrade/registration-0081723672.json")
        .getContentAsString(StandardCharsets.UTF_8);
  }
}
