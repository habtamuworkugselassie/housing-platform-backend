package com.housingplatform.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.housingplatform.BaseIntegrationTest;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.repository.SponsorshipRepository;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.dto.ReservationDtos.Pipeline;
import com.housingplatform.reservation.service.ReservationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class ReservationIT extends BaseIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired SponsorshipRepository packages;
  @Autowired ReservationService service;

  @Test
  void publicReservationLandsInThePipeline() throws Exception {
    Sponsorship pkg =
        packages.save(
            Sponsorship.builder()
                .name("IT Gold " + System.nanoTime())
                .type(Sponsorship.SponsorshipType.GOLD)
                .basePrice(new BigDecimal("900000"))
                .status(Sponsorship.SponsorshipStatus.ACTIVE)
                .build());
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/sponsorship-reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"sponsorshipId\":\""
                            + pkg.getId()
                            + "\",\"company\":\"Noah, PLC\",\"contactName\":\"Sara\",\"email\":\"sara.rsv@example.com\",\"locale\":\"am\"}"))
            .andReturn();
    assertThat(res.getResponse().getStatus()).isEqualTo(201);
    assertThat(res.getResponse().getContentAsString())
        .contains("\"reference\":\"RSV-")
        .contains("Commercial Bank of Ethiopia");

    assertThat(service.search("noah", null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    Pipeline p = service.pipeline();
    assertThat(p.getOpenCount()).isGreaterThanOrEqualTo(1);
    assertThat(p.getOpenValue()).isGreaterThanOrEqualTo(new BigDecimal("900000"));
    assertThat(p.getByStatus()).containsKey(SponsorshipReservation.Status.REQUESTED.name());

    MvcResult bad =
        mockMvc
            .perform(
                post("/api/v1/sponsorship-reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"company\":\"\",\"contactName\":\"\",\"email\":\"x\"}"))
            .andReturn();
    assertThat(bad.getResponse().getStatus()).isEqualTo(400);
  }
}
