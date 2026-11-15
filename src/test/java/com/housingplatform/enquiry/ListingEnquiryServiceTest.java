package com.housingplatform.enquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.enquiry.domain.ListingEnquiry;
import com.housingplatform.enquiry.dto.ListingEnquiryRequest;
import com.housingplatform.enquiry.dto.ListingEnquiryResponse;
import com.housingplatform.enquiry.repository.ListingEnquiryRepository;
import com.housingplatform.enquiry.service.ListingEnquiryMailService;
import com.housingplatform.enquiry.service.impl.ListingEnquiryServiceImpl;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.shared.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ListingEnquiryServiceTest {

  private ListingEnquiryRepository repository;
  private PropertyRepository propertyRepository;
  private OrganizationRepository organizationRepository;
  private ListingEnquiryMailService mailService;
  private ListingEnquiryServiceImpl service;

  @BeforeEach
  void setUp() {
    repository = mock(ListingEnquiryRepository.class);
    propertyRepository = mock(PropertyRepository.class);
    organizationRepository = mock(OrganizationRepository.class);
    mailService = mock(ListingEnquiryMailService.class);
    service =
        new ListingEnquiryServiceImpl(
            repository, propertyRepository, organizationRepository, mailService);
    when(repository.save(any(ListingEnquiry.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void propertyEnquiryLinksTheListingCompanyAndSendsMail() {
    UUID propertyId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    Property property = new Property();
    property.setId(propertyId);
    property.setTitle("Villa in CMC");
    property.setRealEstateCompanyId(orgId);
    Organization org = Organization.builder().name("Noah").build();
    when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
    when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

    ListingEnquiryResponse response =
        service.submit(
            ListingEnquiryRequest.builder()
                .propertyId(propertyId)
                .name("  Sara ")
                .email("sara@example.com")
                .phoneNumber(" ")
                .message("Price negotiable?")
                .build());

    ArgumentCaptor<ListingEnquiry> captor = ArgumentCaptor.forClass(ListingEnquiry.class);
    verify(repository).save(captor.capture());
    ListingEnquiry saved = captor.getValue();
    assertThat(saved.getSubjectType()).isEqualTo(ListingEnquiry.SubjectType.PROPERTY);
    assertThat(saved.getSubjectTitle()).isEqualTo("Villa in CMC");
    assertThat(saved.getOrganization()).isSameAs(org);
    assertThat(saved.getName()).isEqualTo("Sara");
    assertThat(saved.getPhoneNumber()).isNull();
    assertThat(saved.getStatus()).isEqualTo(ListingEnquiry.Status.NEW);
    assertThat(response.getOrganizationName()).isEqualTo("Noah");
    verify(mailService).sendEnquiryEmails(saved);
  }

  @Test
  void organizationEnquiryUsesTheOrganizationAsSubject() {
    UUID orgId = UUID.randomUUID();
    Organization org = Organization.builder().name("Ovid Real Estate").build();
    when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

    service.submit(
        ListingEnquiryRequest.builder()
            .organizationId(orgId)
            .name("Dawit")
            .email("dawit@example.com")
            .message("Do you have units in Ayat?")
            .build());

    ArgumentCaptor<ListingEnquiry> captor = ArgumentCaptor.forClass(ListingEnquiry.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getSubjectType())
        .isEqualTo(ListingEnquiry.SubjectType.ORGANIZATION);
    assertThat(captor.getValue().getSubjectTitle()).isEqualTo("Ovid Real Estate");
  }

  @Test
  void honeypotIsAcceptedButNotStored() {
    ListingEnquiryResponse response =
        service.submit(
            ListingEnquiryRequest.builder()
                .organizationId(UUID.randomUUID())
                .name("bot")
                .email("bot@example.com")
                .message("buy now")
                .website("http://spam.example")
                .build());
    assertThat(response.getId()).isNull();
    verify(repository, never()).save(any());
    verify(mailService, never()).sendEnquiryEmails(any());
  }

  @Test
  void rejectsAnEnquiryWithoutASubject() {
    assertThatThrownBy(
            () ->
                service.submit(
                    ListingEnquiryRequest.builder()
                        .name("x")
                        .email("x@example.com")
                        .message("hi")
                        .build()))
        .isInstanceOf(BusinessException.class);
  }
}
