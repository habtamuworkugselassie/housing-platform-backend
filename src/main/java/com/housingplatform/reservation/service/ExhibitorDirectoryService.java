package com.housingplatform.reservation.service;

import com.housingplatform.identity.dto.SponsoredOrganizationResponse;
import com.housingplatform.identity.service.SponsorshipService;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.domain.SponsorshipReservation.Status;
import com.housingplatform.reservation.dto.ReservationDtos.ExhibitorCard;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public "who is exhibiting" list: every organization with an active, approved sponsorship
 * (paid reservations create one; admins can assign one by hand), with its booths from the
 * reservation that paid. Logos are the approved ones only.
 */
@Service
@RequiredArgsConstructor
public class ExhibitorDirectoryService {

  private final SponsorshipService sponsorships;
  private final SponsorshipReservationRepository reservations;
  private final BoothService boothService;

  @Transactional(readOnly = true)
  public List<ExhibitorCard> list() {
    Map<UUID, List<String>> boothsByOrg = new HashMap<>();
    for (SponsorshipReservation r :
        reservations.findByStatusInOrderByCreatedAtDesc(
            List.of(Status.DEPOSIT_PAID, Status.PAID))) {
      if (r.getOrganization() == null) continue;
      List<String> codes = boothService.codesFor(r.getId());
      if (!codes.isEmpty())
        boothsByOrg
            .computeIfAbsent(r.getOrganization().getId(), k -> new ArrayList<>())
            .addAll(codes);
    }
    List<ExhibitorCard> out = new ArrayList<>();
    for (SponsoredOrganizationResponse o : sponsorships.getActiveSponsoredOrganizations()) {
      out.add(
          ExhibitorCard.builder()
              .organizationId(o.getId())
              .name(o.getName())
              .logoUrl(o.getLogoUrl())
              .organizationType(o.getOrganizationType())
              .city(o.getCity())
              .packageType(o.getSponsorshipType())
              .partnerRole(o.getPartnerRole())
              .boothCodes(boothsByOrg.getOrDefault(o.getId(), List.of()))
              .build());
    }
    return out;
  }
}
