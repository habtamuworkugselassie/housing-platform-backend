package com.housingplatform.etradeservice.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.housingplatform.etradeservice.client.EtradeClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/** With no keys configured the API is open (private-network deployments). */
@SpringBootTest(properties = "etrade.api-keys=")
@AutoConfigureMockMvc
class OpenAccessApiTest {

  @Autowired private MockMvc mvc;
  @MockBean private EtradeClient client;

  @Test
  void noKeysConfiguredMeansNoAuth() throws Exception {
    when(client.findRegistrationByTin("0081723673")).thenReturn(Optional.empty());
    mvc.perform(get("/api/v1/businesses/0081723673")).andExpect(status().isNotFound());
  }
}
