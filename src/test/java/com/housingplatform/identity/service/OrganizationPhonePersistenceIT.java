package com.housingplatform.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.housingplatform.BaseIntegrationTest;
import com.housingplatform.exhibition.dto.ExhibitionInterestRequest;
import com.housingplatform.exhibition.service.ExhibitionInterestService;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.dto.AdminOrganizationCreateRequest;
import com.housingplatform.identity.dto.OrganizationPhoneDto;
import com.housingplatform.identity.dto.OrganizationResponse;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Phone numbers given at organization creation must be written to organization_phones and come back
 * on a reload — for an admin-created organization and for one created by a public exhibition
 * interest signup. Written while chasing a report that phones were not persisted; both paths pass,
 * and this keeps them that way.
 */
class OrganizationPhonePersistenceIT extends BaseIntegrationTest {

  @Autowired OrganizationService organizationService;
  @Autowired ExhibitionInterestService exhibitionInterestService;
  @Autowired EntityManager em;

  @Test
  void adminCreatedOrganizationKeepsItsPhones() {
    AdminOrganizationCreateRequest req = new AdminOrganizationCreateRequest();
    req.setName("Phone Test PLC");
    req.setType(Organization.OrganizationType.SUPPLIER);
    req.setEmail("phone-test@example.com");
    req.setPhoneNumbers(
        List.of(
            OrganizationPhoneDto.builder().countryCode("+251").number("911000111").build(),
            OrganizationPhoneDto.builder().countryCode("+251").number("922000222").build()));

    OrganizationResponse created = organizationService.createOrganizationAsAdmin(req);
    em.flush();
    em.clear();

    Number rows =
        (Number)
            em.createNativeQuery(
                    "select count(*) from organization_phones p "
                        + "join organization_contacts c on c.id = p.contact_id "
                        + "where c.organization_id = ?1 and p.number <> ''")
                .setParameter(1, created.getId())
                .getSingleResult();
    assertThat(rows.intValue()).isEqualTo(2);

    OrganizationResponse reloaded =
        organizationService.getOrganizationByIdUnrestricted(created.getId());
    assertThat(reloaded.getPhoneNumbers())
        .extracting(OrganizationPhoneDto::getNumber)
        .containsExactly("911000111", "922000222");
  }

  @Test
  void publicInterestSignupKeepsThePhoneOnTheOrganization() {
    ExhibitionInterestRequest req = new ExhibitionInterestRequest();
    req.setEmail("interest-" + UUID.randomUUID() + "@example.com");
    req.setCompany("Interest Co");
    req.setOrganizationType("SUPPLIER");
    req.setInterestType("visitor");
    req.setPhoneNumber("+251933000333");

    exhibitionInterestService.register(req);
    em.flush();
    em.clear();

    List<Object> rows =
        em.createNativeQuery(
                "select p.number from organization_phones p "
                    + "join organization_contacts c on c.id = p.contact_id "
                    + "join organizations o on o.id = c.organization_id "
                    + "where o.name = 'Interest Co'")
            .getResultList();
    assertThat(rows).containsExactly((Object) "+251933000333");
  }
}
