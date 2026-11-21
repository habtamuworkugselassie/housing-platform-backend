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
  @Autowired com.housingplatform.reservation.repository.BoothRepository booths;

  @Test
  void publicReservationLandsInThePipeline() throws Exception {
    // H2 tests build the schema from the entities, so seed the plan the migration would provide.
    for (String code : new String[] {"A101", "B101", "B102"}) {
      if (booths.findByCodeInIgnoreCase(java.util.List.of(code)).isEmpty()) {
        booths.save(
            com.housingplatform.reservation.domain.Booth.builder()
                .code(code)
                .block(code.substring(0, 1))
                .mapCol(code.startsWith("A") ? 1 : 3)
                .mapRow(1)
                .boothType(
                    code.equals("A101")
                        ? com.housingplatform.reservation.domain.Booth.Type.PREMIUM
                        : com.housingplatform.reservation.domain.Booth.Type.CORNER)
                .sizeSqm(9)
                .status(com.housingplatform.reservation.domain.Booth.Status.AVAILABLE)
                .build());
      }
    }
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
                            + "\",\"company\":\"Noah, PLC\",\"contactName\":\"Sara\",\"email\":\"sara.rsv@example.com\",\"locale\":\"am\",\"boothCodes\":[\"B101\",\"B102\"]}"))
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
