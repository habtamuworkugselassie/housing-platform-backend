package com.housingplatform.reservation.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationContact;
import com.housingplatform.identity.domain.OrganizationPhone;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every reservation belongs to an organization from the moment it is created: the one an admin
 * picked, else the one the contact already has (by account or contact email), else a new one built
 * from the reservation. The organization stays PENDING_APPROVAL (not public) until a payment
 * provisions the exhibitor account.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationOrganizationLinker {

  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final SponsorshipReservationRepository reservations;

  @Transactional
  public Organization link(SponsorshipReservation r, UUID chosenOrganizationId) {
    Organization org =
        chosenOrganizationId != null
            ? organizations
                .findById(chosenOrganizationId)
                .orElseThrow(
                    () -> new ResourceNotFoundException("Organization", chosenOrganizationId))
            : findOrCreate(r);
    r.setOrganization(org);
    return org;
  }

  /** Links every reservation that still has no organization; returns how many were linked. */
  @Transactional
  public int linkAllMissing() {
    int n = 0;
    for (SponsorshipReservation r : reservations.findByOrganizationIsNull()) {
      try {
        link(r, null);
        reservations.save(r);
        n++;
      } catch (RuntimeException e) {
        log.warn("Reservation {} could not be linked: {}", r.getReference(), e.getMessage());
      }
    }
    return n;
  }

  public Organization findOrCreate(SponsorshipReservation r) {
    String email = r.getEmail().trim().toLowerCase();
    Optional<Organization> byUser =
        users.findByEmail(email).map(User::getOrganization).filter(o -> o != null);
    if (byUser.isPresent()) return byUser.get();
    List<Organization> byContact = organizations.findByContactEmail(email);
    if (!byContact.isEmpty()) return byContact.get(0);
    List<Organization> byName = organizations.findByNameIgnoreCase(r.getCompany().trim());
    if (!byName.isEmpty()) return byName.get(0);

    Organization org =
        Organization.builder()
            .name(r.getCompany().trim())
            .type(Organization.OrganizationType.SUPPLIER)
            .status(Organization.OrganizationStatus.PENDING_APPROVAL)
            .tinNumber(r.getTin())
            .country("Ethiopia")
            .build();
    OrganizationContact contact =
        OrganizationContact.builder().organization(org).email(email).build();
    org.setContact(contact);
    if (r.getPhone() != null && !r.getPhone().isBlank()) {
      contact
          .getPhones()
          .add(
              OrganizationPhone.builder()
                  .contact(contact)
                  .countryCode("+251")
                  .number(r.getPhone().trim())
                  .displayOrder(0)
                  .build());
    }
    Organization saved = organizations.save(org);
    log.info("Organization {} created from reservation {}", saved.getId(), r.getReference());
    return saved;
  }
}
