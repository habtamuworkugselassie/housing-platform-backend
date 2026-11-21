package com.housingplatform.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.housingplatform.BaseIntegrationTest;
import com.housingplatform.visitor.domain.VisitorRegistration;
import com.housingplatform.visitor.dto.VisitorDtos.Stats;
import com.housingplatform.visitor.repository.VisitorRegistrationRepository;
import com.housingplatform.visitor.service.VisitorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class VisitorRegistrationIT extends BaseIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired VisitorRegistrationRepository repo;
  @Autowired VisitorService service;

  @Test
  void publicRegisterBadgeAndQrWorkAndAdminQueriesRunOnTheDatabase() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/visitors/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"fullName\":\"Sara Tesfaye\",\"email\":\"sara.it@example.com\",\"visitorType\":\"investor\",\"organization\":\"Acme, PLC\",\"locale\":\"am\"}"))
            .andReturn();
    assertThat(res.getResponse().getStatus()).isEqualTo(201);
    String body = res.getResponse().getContentAsString();
    assertThat(body).contains("\"code\":\"EBC-");
    String code = body.replaceAll(".*\"code\":\"(EBC-[A-Z0-9]{6})\".*", "$1");

    MvcResult badge = mockMvc.perform(get("/api/v1/visitors/" + code)).andReturn();
    assertThat(badge.getResponse().getStatus()).isEqualTo(200);
    assertThat(badge.getResponse().getContentAsString())
        .contains("Sara Tesfaye")
        .contains("INVESTOR");

    MvcResult qr = mockMvc.perform(get("/api/v1/visitors/" + code + "/qr")).andReturn();
    assertThat(qr.getResponse().getStatus()).isEqualTo(200);
    assertThat(qr.getResponse().getContentType()).startsWith("image/png");
    assertThat(qr.getResponse().getContentAsByteArray().length).isGreaterThan(200);

    assertThat(
            mockMvc
                .perform(get("/api/v1/visitors/EBC-NOPE99"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(404);

    // Admin-side queries against the real schema (H2 here, Postgres in production).
    assertThat(service.search("acme", null, null, PageRequest.of(0, 10)).getTotalElements())
        .isEqualTo(1);
    assertThat(
            service
                .search(null, VisitorRegistration.Status.CHECKED_IN, null, PageRequest.of(0, 10))
                .getTotalElements())
        .isZero();
    service.checkIn(code, "it");
    Stats stats = service.stats();
    assertThat(stats.getTotal()).isGreaterThanOrEqualTo(1);
    assertThat(stats.getCheckedIn()).isGreaterThanOrEqualTo(1);
    assertThat(stats.getByType().get("INVESTOR")).isGreaterThanOrEqualTo(1);
    assertThat(stats.getByDay()).hasSize(7);
    assertThat(service.exportCsv()).contains("\"Acme, PLC\"").contains(code);
  }
}
