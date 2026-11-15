package com.housingplatform.enquiry.service.impl;

import com.housingplatform.enquiry.domain.ListingEnquiry;
import com.housingplatform.enquiry.dto.ListingEnquiryRequest;
import com.housingplatform.enquiry.dto.ListingEnquiryResponse;
import com.housingplatform.enquiry.repository.ListingEnquiryRepository;
import com.housingplatform.enquiry.service.ListingEnquiryMailService;
import com.housingplatform.enquiry.service.ListingEnquiryService;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListingEnquiryServiceImpl implements ListingEnquiryService {

  private final ListingEnquiryRepository repository;
  private final PropertyRepository propertyRepository;
  private final OrganizationRepository organizationRepository;
  private final ListingEnquiryMailService mailService;

  @Override
  @Transactional
  public ListingEnquiryResponse submit(ListingEnquiryRequest request) {
    if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
      // Honeypot tripped: answer like a success so bots learn nothing, store nothing.
      log.info("Listing enquiry honeypot tripped for {}", request.getEmail());
      return ListingEnquiryResponse.builder().status(ListingEnquiry.Status.NEW.name()).build();
    }
    if (request.getPropertyId() == null && request.getOrganizationId() == null) {
      throw new BusinessException("An enquiry needs a property or an organization");
    }

    ListingEnquiry.ListingEnquiryBuilder<?, ?> builder =
        ListingEnquiry.builder()
            .name(request.getName().trim())
            .email(request.getEmail().trim())
            .phoneNumber(blankToNull(request.getPhoneNumber()))
            .message(request.getMessage().trim())
            .pageUrl(blankToNull(request.getPageUrl()))
            .status(ListingEnquiry.Status.NEW);

    if (request.getPropertyId() != null) {
      Property property =
          propertyRepository
              .findById(request.getPropertyId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Property", request.getPropertyId()));
      builder.subjectType(ListingEnquiry.SubjectType.PROPERTY).property(property);
      builder.subjectTitle(property.getTitle());
      if (property.getRealEstateCompanyId() != null) {
        organizationRepository
            .findById(property.getRealEstateCompanyId())
            .ifPresent(builder::organization);
      }
    } else {
      Organization organization =
          organizationRepository
              .findById(request.getOrganizationId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Organization", request.getOrganizationId()));
      builder
          .subjectType(ListingEnquiry.SubjectType.ORGANIZATION)
          .organization(organization)
          .subjectTitle(organization.getName());
    }

    ListingEnquiry saved = repository.save(builder.build());
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              mailService.sendEnquiryEmails(saved);
            }
          });
    } else {
      mailService.sendEnquiryEmails(saved);
    }
    return toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ListingEnquiryResponse> adminList(ListingEnquiry.Status status, Pageable pageable) {
    Page<ListingEnquiry> page =
        status == null
            ? repository.findAllByOrderByCreatedAtDesc(pageable)
            : repository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
    return page.map(ListingEnquiryServiceImpl::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public long countNew() {
    return repository.countByStatus(ListingEnquiry.Status.NEW);
  }

  @Override
  @Transactional
  public ListingEnquiryResponse updateStatus(UUID id, ListingEnquiry.Status status) {
    ListingEnquiry enquiry =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ListingEnquiry", id));
    enquiry.setStatus(status);
    return toResponse(repository.save(enquiry));
  }

  static ListingEnquiryResponse toResponse(ListingEnquiry e) {
    Organization o = e.getOrganization();
    return ListingEnquiryResponse.builder()
        .id(e.getId())
        .subjectType(e.getSubjectType() != null ? e.getSubjectType().name() : null)
        .propertyId(e.getProperty() != null ? e.getProperty().getId() : null)
        .organizationId(o != null ? o.getId() : null)
        .organizationName(o != null ? o.getName() : null)
        .subjectTitle(e.getSubjectTitle())
        .name(e.getName())
        .email(e.getEmail())
        .phoneNumber(e.getPhoneNumber())
        .message(e.getMessage())
        .status(e.getStatus() != null ? e.getStatus().name() : null)
        .pageUrl(e.getPageUrl())
        .createdAt(e.getCreatedAt())
        .updatedAt(e.getUpdatedAt())
        .build();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
