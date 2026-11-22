package com.housingplatform.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.housingplatform.BaseIntegrationTest;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.domain.SponsorshipApplication;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.PasswordResetTokenRepository;
import com.housingplatform.identity.repository.SponsorshipApplicationRepository;
import com.housingplatform.identity.repository.SponsorshipRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.dto.ReservationDtos.AdminRow;
import com.housingplatform.reservation.dto.ReservationDtos.AdminUpdate;
import com.housingplatform.reservation.dto.ReservationDtos.ReserveRequest;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import com.housingplatform.reservation.service.ExhibitorProvisioningService;
import com.housingplatform.reservation.service.ReservationService;
import com.housingplatform.shared.security.JwtTokenProvider;
import com.housingplatform.shared.security.PortalScope;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** A paid reservation becomes an exhibitor account whose uploads wait for admin review. */
@AutoConfigureMockMvc
class ExhibitorProvisioningIT extends BaseIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired SponsorshipRepository packages;
  @Autowired ReservationService reservations;
  @Autowired SponsorshipReservationRepository reservationRepo;
  @Autowired ExhibitorProvisioningService provisioning;
  @Autowired OrganizationRepository organizations;
  @Autowired UserRepository users;
  @Autowired SponsorshipApplicationRepository applications;
  @Autowired PasswordResetTokenRepository tokens;
  @Autowired JwtTokenProvider jwt;
  @Autowired com.housingplatform.exhibition.service.ExhibitionInterestService interestService;
  @Autowired com.housingplatform.exhibition.service.AdminExhibitionInterestService adminLeads;

  @Test
  void paidReservationBecomesAnExhibitorAccountWithReviewedMedia() throws Exception {
    Sponsorship pkg =
        packages.save(
            Sponsorship.builder()
                .name("IT Silver " + System.nanoTime())
                .type(Sponsorship.SponsorshipType.SILVER)
                .basePrice(new BigDecimal("450000"))
                .status(Sponsorship.SponsorshipStatus.ACTIVE)
                .build());
    String email = "lead-" + System.nanoTime() + "@example.com";
    ReserveRequest req = new ReserveRequest();
    req.setSponsorshipId(pkg.getId());
    req.setCompany("Abay Steel PLC");
    req.setContactName("Hana Bekele");
    req.setEmail(email);
    req.setPhone("0911000000");
    req.setLocale("en");
    String reference = reservations.reserve(req).getReference();
    SponsorshipReservation r = reservationRepo.findByReferenceIgnoreCase(reference).orElseThrow();
    // Linked from the first minute, long before any payment.
    assertThat(r.getOrganization()).isNotNull();
    assertThat(r.getOrganization().getName()).isEqualTo("Abay Steel PLC");
    assertThat(r.getOrganization().getStatus())
        .isEqualTo(Organization.OrganizationStatus.PENDING_APPROVAL);
    // ...and it is a lead too, so the Leads page lists every contact.
    assertThat(r.getInterest()).isNotNull();
    assertThat(r.getInterest().getInterestType()).isEqualTo("exhibitor");
    assertThat(r.getInterest().getOrganization().getId()).isEqualTo(r.getOrganization().getId());
    MvcResult lookup =
        mockMvc.perform(get("/api/v1/organizations/lookup").param("q", "abay")).andReturn();
    assertThat(lookup.getResponse().getStatus()).isEqualTo(200);
    assertThat(lookup.getResponse().getContentAsString())
        .contains("Abay Steel PLC")
        .contains("PENDING_APPROVAL");
    MvcResult byEmail =
        mockMvc.perform(get("/api/v1/organizations/match").param("email", email)).andReturn();
    assertThat(byEmail.getResponse().getContentAsString())
        .contains("Abay Steel PLC")
        .contains("\"matchedBy\":\"email\"");
    MvcResult byPhone =
        mockMvc
            .perform(get("/api/v1/organizations/match").param("phone", "+251 911 000 000"))
            .andReturn();
    assertThat(byPhone.getResponse().getContentAsString()).contains("\"matchedBy\":\"phone\"");
    MvcResult none =
        mockMvc.perform(get("/api/v1/organizations/match").param("name", "Nobody Ltd")).andReturn();
    assertThat(none.getResponse().getStatus()).isEqualTo(204);

    // Before any payment the account is refused.
    try {
      provisioning.provision(r.getId(), false);
      throw new AssertionError("expected refusal before payment");
    } catch (com.housingplatform.shared.exception.BusinessException expected) {
    }

    AdminUpdate u = new AdminUpdate();
    u.setAmountPaid(new BigDecimal("75000"));
    AdminRow row = reservations.update(r.getId(), u);
    assertThat(row.getStatus()).isEqualTo("DEPOSIT_PAID");

    AdminRow provisioned = provisioning.provision(r.getId(), false);
    assertThat(provisioned.getOrganizationId()).isNotNull();
    assertThat(provisioned.getExhibitorInvitedAt()).isNotNull();

    Organization org = organizations.findById(provisioned.getOrganizationId()).orElseThrow();
    assertThat(org.getName()).isEqualTo("Abay Steel PLC");
    assertThat(org.getStatus()).isEqualTo(Organization.OrganizationStatus.APPROVED);
    assertThat(org.getContact().getEmail()).isEqualTo(email);
    User user = users.findByEmail(email).orElseThrow();
    assertThat(user.getFirstName()).isEqualTo("Hana");
    assertThat(user.getLastName()).isEqualTo("Bekele");
    assertThat(user.getOrganization().getId()).isEqualTo(org.getId());
    assertThat(org.getPrimaryContact().getId()).isEqualTo(user.getId());
    List<SponsorshipApplication> apps = applications.findByOrganizationId(org.getId());
    assertThat(apps)
        .anyMatch(
            a ->
                a.getStatus() == SponsorshipApplication.ApplicationStatus.APPROVED
                    && a.getSponsorship().getId().equals(pkg.getId()));

    // Second run without a re-invite leaves everything as it is.
    AdminRow again = provisioning.provision(r.getId(), false);
    assertThat(again.getOrganizationId()).isEqualTo(org.getId());
    assertThat(organizations.findByContactEmail(email)).hasSize(1);

    // The exhibitor signs in, sees its organization and reservation, and uploads a logo.
    String exhibitor =
        jwt.generateToken(
            user.getId(), email, List.of(PortalScope.SUPPLIER), List.of("SUPPLIER"), org.getId());
    MvcResult mine =
        mockMvc
            .perform(
                get("/api/v1/organizations/mine").header("Authorization", "Bearer " + exhibitor))
            .andReturn();
    assertThat(mine.getResponse().getStatus()).isEqualTo(200);
    assertThat(mine.getResponse().getContentAsString()).contains("Abay Steel PLC");
    MvcResult mineRes =
        mockMvc
            .perform(
                get("/api/v1/sponsorship-reservations/mine")
                    .header("Authorization", "Bearer " + exhibitor))
            .andReturn();
    assertThat(mineRes.getResponse().getStatus()).isEqualTo(200);
    assertThat(mineRes.getResponse().getContentAsString()).contains(reference);

    MvcResult upload =
        mockMvc
            .perform(
                multipart("/api/v1/organizations/" + org.getId() + "/media")
                    .file(
                        new MockMultipartFile(
                            "files", "logo.png", "image/png", new byte[] {1, 2, 3}))
                    .param("mediaKind", "LOGO")
                    .header("Authorization", "Bearer " + exhibitor))
            .andReturn();
    assertThat(upload.getResponse().getStatus()).isEqualTo(200);
    assertThat(upload.getResponse().getContentAsString()).contains("\"reviewStatus\":\"PENDING\"");

    // Anonymous visitors do not see the pending logo.
    MvcResult pub = mockMvc.perform(get("/api/v1/organizations/" + org.getId())).andReturn();
    assertThat(pub.getResponse().getStatus()).isEqualTo(200);
    assertThat(pub.getResponse().getContentAsString()).doesNotContain("logo.png");
    assertThat(pub.getResponse().getContentAsString()).contains("\"logoUrl\":null");

    // The exhibitor may not edit a stranger's organization, but may edit its own profile.
    String admin =
        jwt.generateToken(
            UUID.randomUUID(), "admin@example.com", List.of(PortalScope.ADMIN), List.of("ADMIN"));
    MvcResult pending =
        mockMvc
            .perform(
                get("/api/v1/organizations/media-reviews/pending")
                    .header("Authorization", "Bearer " + admin))
            .andReturn();
    assertThat(pending.getResponse().getStatus()).isEqualTo(200);
    assertThat(pending.getResponse().getContentAsString()).contains("Abay Steel PLC");
    String attachmentId =
        pending.getResponse().getContentAsString().replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");

    MvcResult review =
        mockMvc
            .perform(
                patch("/api/v1/organizations/" + org.getId() + "/media/" + attachmentId + "/review")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"APPROVED\"}")
                    .header("Authorization", "Bearer " + admin))
            .andReturn();
    assertThat(review.getResponse().getStatus()).isEqualTo(200);
    MvcResult pubAfter = mockMvc.perform(get("/api/v1/organizations/" + org.getId())).andReturn();
    assertThat(pubAfter.getResponse().getContentAsString())
        .contains("\"id\":\"" + attachmentId + "\"")
        .contains("\"reviewStatus\":\"APPROVED\"")
        .doesNotContain("\"logoUrl\":null");

    MvcResult edit =
        mockMvc
            .perform(
                put("/api/v1/organizations/" + org.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Abay Steel PLC\",\"type\":\"SUPPLIER\",\"city\":\"Addis"
                            + " Ababa\",\"description\":\"Steel for builders\"}")
                    .header("Authorization", "Bearer " + exhibitor))
            .andReturn();
    assertThat(edit.getResponse().getStatus()).isEqualTo(200);
    assertThat(edit.getResponse().getContentAsString()).contains("Steel for builders");
  }

  @Test
  void manualReservationAttachesTheChosenOrganization() {
    Organization existing =
        organizations.save(
            Organization.builder()
                .name("Existing Cement SC")
                .type(Organization.OrganizationType.SUPPLIER)
                .status(Organization.OrganizationStatus.APPROVED)
                .build());
    Sponsorship pkg =
        packages.save(
            Sponsorship.builder()
                .name("IT Gold " + System.nanoTime())
                .type(Sponsorship.SponsorshipType.GOLD)
                .basePrice(new BigDecimal("900000"))
                .status(Sponsorship.SponsorshipStatus.ACTIVE)
                .build());
    com.housingplatform.reservation.dto.ReservationDtos.AdminCreate c =
        new com.housingplatform.reservation.dto.ReservationDtos.AdminCreate();
    c.setSponsorshipId(pkg.getId());
    c.setOrganizationId(existing.getId());
    c.setCompany("Existing Cement");
    c.setContactName("Dawit");
    c.setEmail("dawit-" + System.nanoTime() + "@example.com");
    c.setSendEmail(false);
    AdminRow row = reservations.createManual(c, "admin");
    assertThat(row.getOrganizationId()).isEqualTo(existing.getId());
    assertThat(row.getOrganizationName()).isEqualTo("Existing Cement SC");
    assertThat(row.getInterestId()).isNotNull();
    com.housingplatform.reservation.dto.ReservationDtos.OrganizationExpo expo =
        reservations.expoFor(existing.getId());
    assertThat(expo.getReservations()).extracting("reference").contains(row.getReference());
    assertThat(expo.getLeads()).hasSize(1);
    assertThat(expo.getLeads().get(0).getPackageName()).isEqualTo(pkg.getName());

    // The public lead form can pick the same organization instead of creating a duplicate.
    com.housingplatform.exhibition.dto.ExhibitionInterestRequest lead =
        new com.housingplatform.exhibition.dto.ExhibitionInterestRequest();
    lead.setEmail("sales-" + System.nanoTime() + "@example.com");
    lead.setInterestType("exhibitor");
    lead.setOrganizationType("SUPPLIER");
    lead.setSponsorshipId(pkg.getId());
    lead.setOrganizationId(existing.getId());
    com.housingplatform.exhibition.dto.ExhibitionInterestResponse res =
        interestService.register(lead);
    assertThat(res.getOrganizationId()).isEqualTo(existing.getId());
    assertThat(organizations.findByNameIgnoreCase("Existing Cement SC")).hasSize(1);
    org.springframework.data.domain.Page<
            com.housingplatform.exhibition.dto.AdminExhibitionInterestResponse>
        found =
            adminLeads.list(
                org.springframework.data.domain.PageRequest.of(0, 10),
                "existing cement",
                "exhibitor");
    assertThat(found.getContent()).anyMatch(l -> l.getEmail().equals(lead.getEmail()));
    assertThat(
            adminLeads.list(
                org.springframework.data.domain.PageRequest.of(0, 10), "zzz-nomatch", null))
        .isEmpty();
  }

  @Test
  void splitsContactNames() {
    assertThat(ExhibitorProvisioningService.splitName("Hana Bekele Alemu"))
        .containsExactly("Hana", "Bekele Alemu");
    assertThat(ExhibitorProvisioningService.splitName("Hana")).containsExactly("Hana", "-");
    assertThat(ExhibitorProvisioningService.splitName(" ")).containsExactly("Exhibitor", "Contact");
  }
}
