package com.housingplatform.etradeservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.etradeservice.EtradeUnavailableException;
import com.housingplatform.etradeservice.client.EtradeClient;
import com.housingplatform.etradeservice.client.EtradeRegistrationResponse;
import com.housingplatform.etradeservice.config.ApiKeyFilter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

/** Full HTTP surface with the upstream client mocked: auth, mapping, error codes, caching. */
@SpringBootTest(properties = {"etrade.api-keys=key-one,key-two", "etrade.cache-ttl=1h"})
@AutoConfigureMockMvc
class BusinessApiTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @MockBean private EtradeClient client;

  private EtradeRegistrationResponse fixture() throws Exception {
    return json.readValue(
        new ClassPathResource("etrade/registration-0081723672.json").getInputStream(),
        EtradeRegistrationResponse.class);
  }

  @Test
  void rejectsRequestsWithoutAValidApiKey() throws Exception {
    mvc.perform(get("/api/v1/businesses/0081723672")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/businesses/0081723672").header(ApiKeyFilter.HEADER, "nope"))
        .andExpect(status().isUnauthorized());
    verify(client, never()).findRegistrationByTin(any());
  }

  @Test
  void healthStaysOpenWithoutAKey() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void returnsTheMappedRecordAndCachesTheUpstreamCall() throws Exception {
    when(client.findRegistrationByTin("0081723672")).thenReturn(Optional.of(fixture()));

    for (String key : new String[] {"key-one", "key-two"}) {
      mvc.perform(get("/api/v1/businesses/0081-723-672").header(ApiKeyFilter.HEADER, key))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.tin").value("0081723672"))
          .andExpect(jsonPath("$.businessName").value("FIREW WONDE BELAY"))
          .andExpect(jsonPath("$.legalCondition.name").value("Sole Proprietorship"))
          .andExpect(jsonPath("$.registrationNumber").value("AA/LK/14/1/3631426/2016"))
          .andExpect(jsonPath("$.registrationDate").value("2022-12-01"))
          .andExpect(jsonPath("$.businesses[0].licenseNumber").value("YK/AA/14/672/5990336/2016"))
          .andExpect(jsonPath("$.businesses[0].sectors[0].code").value(62222))
          .andExpect(jsonPath("$.associates[0].name").value("FIREW WONDE"))
          .andExpect(jsonPath("$.associates[0].photoDataUrl").doesNotExist())
          .andExpect(
              jsonPath("$.sourceUrl")
                  .value("https://etrade.gov.et/business-license-checker?tin=0081723672"));
    }
    verify(client).findRegistrationByTin("0081723672");

    mvc.perform(
            get("/api/v1/businesses/0081723672")
                .param("includePhotos", "true")
                .header(ApiKeyFilter.HEADER, "key-one"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.associates[0].photoDataUrl")
                .value("data:image/jpeg;base64,/9j/placeholder"));
  }

  @Test
  void malformedTinIs400AndNeverReachesEtrade() throws Exception {
    mvc.perform(get("/api/v1/businesses/123").header(ApiKeyFilter.HEADER, "key-one"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("TIN must be exactly 10 digits, e.g. 0081723672"));
    verify(client, never()).findRegistrationByTin(any());
  }

  @Test
  void unknownTinIs404() throws Exception {
    when(client.findRegistrationByTin("0081723673")).thenReturn(Optional.empty());
    mvc.perform(get("/api/v1/businesses/0081723673").header(ApiKeyFilter.HEADER, "key-one"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.message")
                .value("No business is registered on eTrade under TIN 0081723673"));
  }

  @Test
  void upstreamFailureIs502() throws Exception {
    when(client.findRegistrationByTin("0081723674"))
        .thenThrow(new EtradeUnavailableException("eTrade (etrade.gov.et) could not be reached"));
    mvc.perform(get("/api/v1/businesses/0081723674").header(ApiKeyFilter.HEADER, "key-one"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("Upstream Service Unavailable"));
  }
}
