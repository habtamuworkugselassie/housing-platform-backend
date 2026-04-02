package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationContact;
import com.housingplatform.identity.domain.OrganizationPhone;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.identity.domain.SponsorshipApplication;
import com.housingplatform.identity.domain.SupplierSubcategory;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.AdminOrganizationCreateRequest;
import com.housingplatform.identity.dto.OrganizationMediaItem;
import com.housingplatform.identity.dto.OrganizationPhoneDto;
import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.dto.SupplierSubcategoryIdsRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.SponsorshipApplicationRepository;
import com.housingplatform.identity.repository.SupplierSubcategoryRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.OrganizationMapper;
import com.housingplatform.identity.service.OrganizationPublicVisibility;
import com.housingplatform.identity.service.OrganizationService;
import com.housingplatform.media.domain.MediaAttachment;
import com.housingplatform.media.repository.MediaAttachmentRepository;
import com.housingplatform.media.service.MediaStorageService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationServiceImpl implements OrganizationService {

  private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

  private final OrganizationRepository organizationRepository;
  private final SupplierSubcategoryRepository supplierSubcategoryRepository;
  private final OrganizationMapper organizationMapper;
  private final UserRepository userRepository;
  private final RealEstateAgentRepository realEstateAgentRepository;
  private final SponsorshipApplicationRepository sponsorshipApplicationRepository;
  private final MediaAttachmentRepository mediaAttachmentRepository;
  private final MediaStorageService mediaStorageService;
  private final CacheManager cacheManager;

  @Override
  public OrganizationResponse createOrganization(OrganizationRequest request) {
    // Check if registration number already exists
    if (request.getRegistrationNumber() != null
        && organizationRepository
            .findByRegistrationNumber(request.getRegistrationNumber())
            .isPresent()) {
      throw new BusinessException("Organization with registration number already exists");
    }

    Organization organization = organizationMapper.toEntity(request);
    organization.setStatus(Organization.OrganizationStatus.PENDING_APPROVAL);
    syncPhonesFromRequest(organization, request.getPhoneNumbers());
    Organization saved = organizationRepository.save(organization);

    // If a REALTOR is creating a REAL_ESTATE_COMPANY, automatically create a
    // RealEstateAgent
    // and mark them as the super agent (owner)
    if (saved.getType() == Organization.OrganizationType.REAL_ESTATE_COMPANY) {
      try {
        UUID currentUserId = UserContext.getCurrentUserId();
        User currentUser =
            userRepository
                .findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));

        // Check if user has REALTOR role
        if (currentUser.getRoles().contains(User.UserRole.REALTOR)) {
          // Check if user is already an agent
          if (!realEstateAgentRepository.existsByUserId(currentUserId)) {
            // Create RealEstateAgent and mark as super agent
            RealEstateAgent superAgent =
                RealEstateAgent.builder()
                    .user(currentUser)
                    .organization(saved)
                    .status(RealEstateAgent.AgentStatus.ACTIVE)
                    .isSuperAgent(true)
                    .build();
            realEstateAgentRepository.save(superAgent);
            currentUser.setOrganization(saved);
            userRepository.save(currentUser);
          } else {
            throw new BusinessException(
                "User is already registered as a real estate agent for another organization");
          }
        }
      } catch (IllegalStateException e) {
        // User context not available (shouldn't happen in authenticated endpoint, but
        // handle
        // gracefully)
        // This means the organization was created but no agent was linked
      }
    } else if (saved.getType() == Organization.OrganizationType.BANK) {
      try {
        UUID currentUserId = UserContext.getCurrentUserId();
        User currentUser =
            userRepository
                .findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));
        if (currentUser.getRoles().contains(User.UserRole.BANKER)) {
          if (currentUser.getOrganization() != null
              && !currentUser.getOrganization().getId().equals(saved.getId())) {
            throw new BusinessException(
                "You are already linked to another organization. Contact support to change it.");
          }
          currentUser.setOrganization(saved);
          userRepository.save(currentUser);
          if (saved.getPrimaryContact() == null) {
            saved.setPrimaryContact(currentUser);
            organizationRepository.save(saved);
          }
        }
      } catch (IllegalStateException e) {
        // No security context
      }
    } else if (saved.getType() == Organization.OrganizationType.SUPPLIER) {
      try {
        UUID currentUserId = UserContext.getCurrentUserId();
        User currentUser =
            userRepository
                .findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));
        if (currentUser.getRoles().contains(User.UserRole.SUPPLIER)) {
          if (currentUser.getOrganization() != null
              && !currentUser.getOrganization().getId().equals(saved.getId())) {
            throw new BusinessException(
                "You are already linked to another organization. Contact support to change it.");
          }
          currentUser.setOrganization(saved);
          userRepository.save(currentUser);
          if (saved.getPrimaryContact() == null) {
            saved.setPrimaryContact(currentUser);
            organizationRepository.save(saved);
          }
        }
      } catch (IllegalStateException e) {
        // No security context
      }
    }

    if (request.getSupplierSubcategoryIds() != null) {
      syncSupplierSubcategories(saved, request.getSupplierSubcategoryIds());
      saved = organizationRepository.save(saved);
    }

    OrganizationResponse response = organizationMapper.toResponse(saved);
    enrichWithMedia(response, saved.getId());
    return response;
  }

  @Override
  public OrganizationResponse createOrganizationAsAdmin(AdminOrganizationCreateRequest request) {
    if (request.getRegistrationNumber() != null
        && organizationRepository
            .findByRegistrationNumber(request.getRegistrationNumber())
            .isPresent()) {
      throw new BusinessException("Organization with registration number already exists");
    }

    OrganizationRequest req = new OrganizationRequest();
    req.setName(request.getName());
    req.setRegistrationNumber(request.getRegistrationNumber());
    req.setBusinessRegistration(request.getBusinessRegistration());
    req.setLicense(request.getLicense());
    req.setVatRegistration(request.getVatRegistration());
    req.setTinRegistration(request.getTinRegistration());
    req.setBusinessRegistrationNumber(request.getBusinessRegistrationNumber());
    req.setLicenseNumber(request.getLicenseNumber());
    req.setVatNumber(request.getVatNumber());
    req.setTinNumber(request.getTinNumber());
    req.setType(request.getType());
    req.setAddress(request.getAddress());
    req.setCity(request.getCity());
    req.setCountry(request.getCountry());
    req.setLatitude(request.getLatitude());
    req.setLongitude(request.getLongitude());
    if (request.getPhoneNumbers() != null && !request.getPhoneNumbers().isEmpty()) {
      req.setPhoneNumbers(request.getPhoneNumbers());
    } else if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
      req.setPhoneNumbers(
          List.of(
              OrganizationPhoneDto.builder()
                  .countryCode("+251")
                  .number(request.getPhoneNumber().trim())
                  .build()));
    }
    req.setEmail(request.getEmail());
    req.setWebsite(request.getWebsite());
    req.setFacebookUrl(request.getFacebookUrl());
    req.setInstagramUrl(request.getInstagramUrl());
    req.setLinkedinUrl(request.getLinkedinUrl());
    req.setTwitterUrl(request.getTwitterUrl());
    req.setYoutubeUrl(request.getYoutubeUrl());
    req.setDescription(request.getDescription());
    req.setPrimaryContactUserId(request.getPrimaryContactUserId());

    Organization organization = organizationMapper.toEntity(req);
    syncPhonesFromRequest(organization, req.getPhoneNumbers());
    organization.setStatus(
        request.getInitialStatus() != null
            ? request.getInitialStatus()
            : Organization.OrganizationStatus.PENDING_APPROVAL);
    Organization saved = organizationRepository.save(organization);
    if (request.getSupplierSubcategoryIds() != null) {
      syncSupplierSubcategories(saved, request.getSupplierSubcategoryIds());
      saved = organizationRepository.save(saved);
    }
    OrganizationResponse response = organizationMapper.toResponse(saved);
    enrichWithMedia(response, saved.getId());
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public OrganizationResponse getOrganizationById(UUID id) {
    Organization organization =
        organizationRepository
            .findByIdWithSupplierSubcategories(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
    if (!canCurrentUserViewOrganization(organization)) {
      throw new ResourceNotFoundException("Organization", id);
    }
    OrganizationResponse response = organizationMapper.toResponse(organization);
    enrichWithMedia(response, id);
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public OrganizationResponse getOrganizationByIdUnrestricted(UUID id) {
    Organization organization =
        organizationRepository
            .findByIdWithSupplierSubcategories(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
    OrganizationResponse response = organizationMapper.toResponse(organization);
    enrichWithMedia(response, id);
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OrganizationResponse> getOrganizationByRegistrationNumber(
      String registrationNumber) {
    if (registrationNumber == null || registrationNumber.isBlank()) {
      return Optional.empty();
    }
    return organizationRepository
        .findByRegistrationNumber(registrationNumber.trim())
        .filter(this::canCurrentUserViewOrganization)
        .map(
            org -> {
              OrganizationResponse response = organizationMapper.toResponse(org);
              enrichWithMedia(response, org.getId());
              return response;
            });
  }

  @Override
  public void evictListingCachesForOrganization(UUID organizationId) {
    evictAvailablePropertiesByOrgCache(organizationId);
    evictSponsorshipCaches();
  }

  /**
   * Public marketplace data is only for {@link Organization.OrganizationStatus#APPROVED}
   * organizations. Members of the organization and admins may still load restricted orgs (e.g.
   * during sponsorship review).
   */
  private boolean canCurrentUserViewOrganization(Organization organization) {
    if (OrganizationPublicVisibility.isPubliclyListed(organization)) {
      return true;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return false;
    }
    if (UserContext.isAdmin()) {
      return true;
    }
    final UUID userId;
    try {
      userId = UserContext.getCurrentUserId();
    } catch (IllegalStateException e) {
      return false;
    }
    if (organization.getPrimaryContact() != null
        && organization.getPrimaryContact().getId().equals(userId)) {
      return true;
    }
    Optional<UUID> jwtOrg = UserContext.getCurrentUserOrganizationId();
    if (jwtOrg.isPresent() && jwtOrg.get().equals(organization.getId())) {
      return true;
    }
    return realEstateAgentRepository
        .findByUserId(userId)
        .map(a -> a.getOrganizationId().equals(organization.getId()))
        .orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public OrganizationResponse getMyOrganization() {
    UUID currentUserId = UserContext.getCurrentUserId();
    RealEstateAgent agent =
        realEstateAgentRepository
            .findByUserId(currentUserId)
            .orElseThrow(
                () -> new ResourceNotFoundException("RealEstateAgent not found for current user"));

    if (!agent.getIsSuperAgent()) {
      throw new BusinessException("Only super agents can access their organization details");
    }

    OrganizationResponse response = organizationMapper.toResponse(agent.getOrganization());
    enrichWithMedia(response, agent.getOrganization().getId());
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public OrganizationResponse getMyBank() {
    UUID currentUserId = UserContext.getCurrentUserId();

    List<Organization> banks =
        organizationRepository.findByType(Organization.OrganizationType.BANK);
    Optional<Organization> asPrimary =
        banks.stream()
            .filter(
                b ->
                    b.getPrimaryContact() != null
                        && b.getPrimaryContact().getId().equals(currentUserId))
            .findFirst();
    if (asPrimary.isPresent()) {
      Organization bank = asPrimary.get();
      OrganizationResponse response = organizationMapper.toResponse(bank);
      enrichWithMedia(response, bank.getId());
      return response;
    }

    User user =
        userRepository
            .findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));
    Organization linked = user.getOrganization();
    if (linked != null && linked.getType() == Organization.OrganizationType.BANK) {
      OrganizationResponse response = organizationMapper.toResponse(linked);
      enrichWithMedia(response, linked.getId());
      return response;
    }

    throw new ResourceNotFoundException(
        "Bank not found for current user. Join a verified bank or register a bank organization.");
  }

  @Override
  @Transactional(readOnly = true)
  public List<OrganizationResponse> getAllOrganizations(String type, String status, String search) {
    List<Organization> organizations;

    // Start with all organizations or search results
    if (search != null && !search.trim().isEmpty()) {
      organizations = organizationRepository.searchOrganizations(search.trim());
    } else {
      organizations = organizationRepository.findAll();
    }

    // Apply type filter if provided
    if (type != null && !type.trim().isEmpty()) {
      try {
        Organization.OrganizationType orgType = Organization.OrganizationType.fromValue(type);
        organizations =
            organizations.stream()
                .filter(org -> org.getType() == orgType)
                .collect(Collectors.toList());
      } catch (IllegalArgumentException e) {
        // Invalid type, ignore filter
      }
    }

    // Apply status filter if provided
    if (status != null && !status.trim().isEmpty()) {
      try {
        // Map frontend status values to backend enum values
        String statusUpper = status.toUpperCase();
        if ("PENDING".equals(statusUpper)) {
          statusUpper = "PENDING_APPROVAL";
        }
        Organization.OrganizationStatus orgStatus =
            Organization.OrganizationStatus.valueOf(statusUpper);
        organizations =
            organizations.stream()
                .filter(org -> org.getStatus() == orgStatus)
                .collect(Collectors.toList());
      } catch (IllegalArgumentException e) {
        // Invalid status, ignore filter
      }
    }

    return organizations.stream()
        .map(
            organization -> {
              OrganizationResponse response = organizationMapper.toResponse(organization);
              enrichWithMedia(response, organization.getId());
              return response;
            })
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<OrganizationResponse> getApprovedOrganizationsForMarketplace(
      String types, UUID subcategoryId) {
    if (types == null || types.trim().isEmpty()) {
      return List.of();
    }
    Set<Organization.OrganizationType> typeSet = new java.util.HashSet<>();
    for (String t : types.split(",")) {
      String trimmed = t.trim();
      if (!trimmed.isEmpty()) {
        try {
          typeSet.add(Organization.OrganizationType.fromValue(trimmed));
        } catch (IllegalArgumentException ignored) {
          // skip invalid type
        }
      }
    }
    if (typeSet.isEmpty()) {
      return List.of();
    }
    List<Organization> all =
        organizationRepository.findByStatusWithSupplierSubcategories(
            Organization.OrganizationStatus.APPROVED);
    List<Organization> filtered =
        all.stream().filter(org -> typeSet.contains(org.getType())).collect(Collectors.toList());
    if (subcategoryId != null) {
      filtered =
          filtered.stream()
              .filter(
                  org ->
                      org.getType() != Organization.OrganizationType.SUPPLIER
                          || (org.getSupplierSubcategories() != null
                              && org.getSupplierSubcategories().stream()
                                  .anyMatch(s -> subcategoryId.equals(s.getId()))))
              .collect(Collectors.toList());
    }
    Set<UUID> marketplaceOrgIds =
        filtered.stream().map(Organization::getId).collect(Collectors.toSet());
    Map<UUID, SponsorshipApplication> bestSponsorshipByOrg =
        resolveBestActiveSponsorshipByOrganizationId(marketplaceOrgIds);

    List<OrganizationResponse> responses = new ArrayList<>();
    for (Organization org : filtered) {
      OrganizationResponse resp = organizationMapper.toResponse(org);
      enrichWithMedia(resp, org.getId());
      SponsorshipApplication active = bestSponsorshipByOrg.get(org.getId());
      if (active != null && active.getSponsorship() != null) {
        resp.setIsSponsored(true);
        resp.setSponsorshipType(active.getSponsorship().getType().name());
      }
      responses.add(resp);
    }
    responses.sort(marketplaceOrganizationComparator());
    return responses;
  }

  /**
   * For each organization ID, the single best (lowest tier rank) approved active sponsorship
   * application. Skips suspended organizations.
   */
  private Map<UUID, SponsorshipApplication> resolveBestActiveSponsorshipByOrganizationId(
      Set<UUID> limitToOrgIds) {
    if (limitToOrgIds == null || limitToOrgIds.isEmpty()) {
      return Map.of();
    }
    LocalDateTime now = LocalDateTime.now();
    List<SponsorshipApplication> active =
        sponsorshipApplicationRepository.findAllActiveApplications(now);
    Map<UUID, SponsorshipApplication> best = new HashMap<>();
    for (SponsorshipApplication sa : active) {
      if (sa.getOrganization() == null) {
        continue;
      }
      Organization orgEntity = sa.getOrganization();
      if (orgEntity.getStatus() == Organization.OrganizationStatus.SUSPENDED
          || orgEntity.getStatus() == Organization.OrganizationStatus.SPONSORSHIP_PENDING) {
        continue;
      }
      UUID oid = orgEntity.getId();
      if (!limitToOrgIds.contains(oid)) {
        continue;
      }
      if (sa.getSponsorship() == null) {
        continue;
      }
      best.merge(
          oid,
          sa,
          (a, b) ->
              a.getSponsorship().getType().tierRank() <= b.getSponsorship().getType().tierRank()
                  ? a
                  : b);
    }
    return best;
  }

  private static Comparator<OrganizationResponse> marketplaceOrganizationComparator() {
    return Comparator.comparingInt(OrganizationServiceImpl::marketplaceSortTierRank)
        .thenComparing(
            OrganizationResponse::getName, Comparator.nullsLast(String::compareToIgnoreCase));
  }

  private static int marketplaceSortTierRank(OrganizationResponse r) {
    if (!Boolean.TRUE.equals(r.getIsSponsored()) || r.getSponsorshipType() == null) {
      return 100;
    }
    try {
      return Sponsorship.SponsorshipType.valueOf(r.getSponsorshipType().trim().toUpperCase())
          .tierRank();
    } catch (IllegalArgumentException e) {
      return 100;
    }
  }

  @Override
  public OrganizationResponse updateOrganization(UUID id, OrganizationRequest request) {
    Organization organization =
        organizationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", id));

    // Check if current user is super agent of this organization (skip if admin)
    if (!UserContext.isAdmin()) {
      try {
        UUID currentUserId = UserContext.getCurrentUserId();
        RealEstateAgent agent =
            realEstateAgentRepository
                .findByUserId(currentUserId)
                .orElseThrow(() -> new BusinessException("User is not a real estate agent"));

        if (!agent.getIsSuperAgent() || !agent.getOrganizationId().equals(id)) {
          throw new BusinessException("Only super agents can update their own organization");
        }
      } catch (IllegalStateException e) {
        // User context not available - should not happen in authenticated endpoint
        throw new BusinessException("User context not available");
      }
    }

    organizationMapper.updateEntity(organization, request);
    if (request.getPhoneNumbers() != null) {
      syncPhonesFromRequest(organization, request.getPhoneNumbers());
    }
    if (request.getSupplierSubcategoryIds() != null) {
      syncSupplierSubcategories(organization, request.getSupplierSubcategoryIds());
    }
    Organization updated = organizationRepository.save(organization);
    evictAvailablePropertiesByOrgCache(id);
    OrganizationResponse response = organizationMapper.toResponse(updated);
    enrichWithMedia(response, id);
    return response;
  }

  @Override
  public OrganizationResponse approveOrganization(UUID id) {
    Organization organization =
        organizationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", id));

    organization.setStatus(Organization.OrganizationStatus.APPROVED);
    Organization updated = organizationRepository.save(organization);
    OrganizationResponse response = organizationMapper.toResponse(updated);
    enrichWithMedia(response, id);
    return response;
  }

  @Override
  public OrganizationResponse rejectOrganization(UUID id, String reason) {
    Organization organization =
        organizationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", id));

    organization.setStatus(Organization.OrganizationStatus.REJECTED);
    Organization updated = organizationRepository.save(organization);
    OrganizationResponse response = organizationMapper.toResponse(updated);
    enrichWithMedia(response, id);
    return response;
  }

  @Override
  public OrganizationResponse suspendOrganization(UUID id, String reason) {
    Organization organization =
        organizationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", id));

    organization.setStatus(Organization.OrganizationStatus.SUSPENDED);
    Organization updated = organizationRepository.save(organization);

    // Suspend (cancel) all approved sponsorship applications for this organization
    List<SponsorshipApplication> applications =
        sponsorshipApplicationRepository.findByOrganizationId(id);
    for (SponsorshipApplication app : applications) {
      if (app.getStatus() == SponsorshipApplication.ApplicationStatus.APPROVED) {
        app.setStatus(SponsorshipApplication.ApplicationStatus.CANCELLED);
        sponsorshipApplicationRepository.save(app);
      }
    }

    // Evict sponsorship caches so the suspended org is immediately removed from
    // sponsored/exclusive lists
    evictSponsorshipCaches();

    OrganizationResponse response = organizationMapper.toResponse(updated);
    enrichWithMedia(response, id);
    return response;
  }

  @Override
  public OrganizationResponse reactivateOrganization(UUID id) {
    Organization organization =
        organizationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", id));

    if (organization.getStatus() != Organization.OrganizationStatus.SUSPENDED) {
      throw new BusinessException("Only suspended organizations can be reactivated");
    }

    organization.setStatus(Organization.OrganizationStatus.APPROVED);
    Organization updated = organizationRepository.save(organization);

    // Re-activate (set back to APPROVED) sponsorship applications that were
    // cancelled when org was suspended
    List<SponsorshipApplication> applications =
        sponsorshipApplicationRepository.findByOrganizationId(id);
    for (SponsorshipApplication app : applications) {
      if (app.getStatus() == SponsorshipApplication.ApplicationStatus.CANCELLED) {
        app.setStatus(SponsorshipApplication.ApplicationStatus.APPROVED);
        sponsorshipApplicationRepository.save(app);
      }
    }

    // Evict sponsorship caches so the reactivated org is immediately visible again
    evictSponsorshipCaches();

    OrganizationResponse response = organizationMapper.toResponse(updated);
    enrichWithMedia(response, id);
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public OrganizationResponse getMySupplier() {
    UUID currentUserId = UserContext.getCurrentUserId();

    // Find supplier where user is primary contact
    List<Organization> suppliers =
        organizationRepository.findByType(Organization.OrganizationType.SUPPLIER);
    Organization supplier =
        suppliers.stream()
            .filter(
                s ->
                    s.getPrimaryContact() != null
                        && s.getPrimaryContact().getId().equals(currentUserId))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Supplier not found for current user. User must be primary contact of a supplier organization."));

    OrganizationResponse response = organizationMapper.toResponse(supplier);
    enrichWithMedia(response, supplier.getId());
    return response;
  }

  private void syncPhonesFromRequest(
      Organization organization, List<OrganizationPhoneDto> phoneNumbers) {
    OrganizationContact contact = organization.getContact();
    if (contact == null) {
      contact = OrganizationContact.builder().organization(organization).build();
      organization.setContact(contact);
    }
    List<OrganizationPhone> phones = contact.getPhones();
    if (phones == null) {
      phones = new ArrayList<>();
      contact.setPhones(phones);
    }
    phones.clear();
    if (phoneNumbers != null && !phoneNumbers.isEmpty()) {
      int order = 0;
      for (OrganizationPhoneDto dto : phoneNumbers) {
        String num = dto.getNumber() != null ? dto.getNumber().trim() : "";
        if (!num.isEmpty()) {
          OrganizationPhone phone =
              OrganizationPhone.builder()
                  .contact(contact)
                  .countryCode(dto.getCountryCode() != null ? dto.getCountryCode().trim() : "+251")
                  .number(num)
                  .displayOrder(order++)
                  .build();
          phones.add(phone);
        }
      }
    }
    if (phones.isEmpty()) {
      phones.add(
          OrganizationPhone.builder()
              .contact(contact)
              .countryCode("+251")
              .number("")
              .displayOrder(0)
              .build());
    }
  }

  @Transactional(readOnly = true)
  protected void enrichWithMedia(OrganizationResponse response, UUID organizationId) {
    if (response == null || organizationId == null) return;
    List<MediaAttachment> attachments =
        mediaAttachmentRepository.findByOrganizationIdOrderByDisplayOrderAsc(organizationId);
    if (attachments.isEmpty()) return;

    String basePath = "/api/v1/organizations/" + organizationId + "/media/";
    List<OrganizationMediaItem> items = new ArrayList<>();
    String logoUrl = null;
    for (MediaAttachment att : attachments) {
      String url = att.hasFileData() ? basePath + att.getId() + "/file" : att.getImageUrl();
      items.add(
          OrganizationMediaItem.builder()
              .id(att.getId())
              .url(url)
              .caption(att.getCaption())
              .displayOrder(att.getDisplayOrder())
              .isPrimary(att.getIsPrimary())
              .mediaKind(att.getMediaKind() != null ? att.getMediaKind().name() : "IMAGE")
              .build());
      if (logoUrl == null
          && (att.getMediaKind() == MediaAttachment.MediaKind.LOGO || att.getIsPrimary())) {
        logoUrl = url;
      }
    }
    if (logoUrl == null && !items.isEmpty()) {
      logoUrl = items.get(0).getUrl();
    }
    response.setLogoUrl(logoUrl);
    response.setMedia(items);
  }

  @Override
  public OrganizationResponse uploadOrganizationMedia(
      UUID organizationId, List<MultipartFile> files, String mediaKindStr) {
    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));

    if (!UserContext.isAdmin()) {
      UUID currentUserId = UserContext.getCurrentUserId();
      boolean isPrimaryContact =
          organization.getPrimaryContact() != null
              && organization.getPrimaryContact().getId().equals(currentUserId);
      if (!isPrimaryContact) {
        RealEstateAgent agent = realEstateAgentRepository.findByUserId(currentUserId).orElse(null);
        boolean isSuperAgent =
            agent != null
                && Boolean.TRUE.equals(agent.getIsSuperAgent())
                && organization.getId().equals(agent.getOrganizationId());
        if (!isSuperAgent) {
          throw new BusinessException(
              "Only admin or organization primary contact can upload media");
        }
      }
    }

    if (files == null || files.isEmpty()) {
      throw new BusinessException("At least one file must be provided");
    }

    MediaAttachment.MediaKind kind = MediaAttachment.MediaKind.IMAGE;
    if (mediaKindStr != null && !mediaKindStr.isBlank()) {
      try {
        kind = MediaAttachment.MediaKind.valueOf(mediaKindStr.toUpperCase());
      } catch (IllegalArgumentException ignored) {
      }
    }

    List<MediaAttachment> existing =
        mediaAttachmentRepository.findByOrganizationIdOrderByDisplayOrderAsc(organizationId);
    int nextOrder =
        existing.isEmpty()
            ? 0
            : existing.stream().mapToInt(MediaAttachment::getDisplayOrder).max().orElse(0) + 1;

    for (int i = 0; i < files.size(); i++) {
      MultipartFile file = files.get(i);
      if (file.isEmpty()) continue;
      if (file.getSize() > MAX_FILE_SIZE) {
        throw new BusinessException(
            "File " + file.getOriginalFilename() + " exceeds maximum size of 100MB");
      }
      String contentType = file.getContentType();
      if (contentType == null
          || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
        throw new BusinessException(
            "File " + file.getOriginalFilename() + " must be an image or video");
      }
      boolean isVideo = contentType.startsWith("video/");
      MediaAttachment.MediaKind fileKind =
          isVideo
              ? MediaAttachment.MediaKind.VIDEO
              : (kind == MediaAttachment.MediaKind.LOGO
                  ? MediaAttachment.MediaKind.LOGO
                  : MediaAttachment.MediaKind.IMAGE);
      String imageUrl = mediaStorageService.save(file, "organizations/" + organizationId);
      MediaAttachment att =
          MediaAttachment.builder()
              .organization(organization)
              .imageUrl(imageUrl)
              .contentType(contentType)
              .fileName(file.getOriginalFilename())
              .displayOrder(nextOrder + i)
              .isPrimary(existing.isEmpty() && i == 0)
              .mediaKind(fileKind)
              .build();
      mediaAttachmentRepository.save(att);
    }
    mediaAttachmentRepository.flush();
    OrganizationResponse response = organizationMapper.toResponse(organization);
    enrichWithMedia(response, organizationId);
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> getOrganizationMediaFile(UUID organizationId, UUID attachmentId) {
    MediaAttachment att =
        mediaAttachmentRepository
            .findByIdAndOrganizationId(attachmentId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("MediaAttachment", attachmentId));
    String imageUrl = att.getImageUrl();
    if (mediaStorageService.isUploadsUrl(imageUrl)) {
      try (var in = mediaStorageService.getInputStream(imageUrl)) {
        byte[] body = in.readAllBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
            att.getContentType() != null
                ? MediaType.parseMediaType(att.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
      } catch (IOException e) {
        throw new ResourceNotFoundException("Media file not found on disk");
      }
    }
    if (!att.hasFileData()) {
      throw new ResourceNotFoundException("Media file not found");
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(
        att.getContentType() != null
            ? MediaType.parseMediaType(att.getContentType())
            : MediaType.APPLICATION_OCTET_STREAM);
    return ResponseEntity.ok().headers(headers).body(att.getFileData());
  }

  @Override
  public OrganizationResponse deleteOrganizationMedia(UUID organizationId, UUID attachmentId) {
    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));

    if (!UserContext.isAdmin()) {
      UUID currentUserId = UserContext.getCurrentUserId();
      boolean isPrimaryContact =
          organization.getPrimaryContact() != null
              && organization.getPrimaryContact().getId().equals(currentUserId);
      if (!isPrimaryContact) {
        RealEstateAgent agent = realEstateAgentRepository.findByUserId(currentUserId).orElse(null);
        boolean isSuperAgent =
            agent != null
                && Boolean.TRUE.equals(agent.getIsSuperAgent())
                && organization.getId().equals(agent.getOrganizationId());
        if (!isSuperAgent) {
          throw new BusinessException(
              "Only admin or organization primary contact can delete media");
        }
      }
    }

    MediaAttachment att =
        mediaAttachmentRepository
            .findByIdAndOrganizationId(attachmentId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("MediaAttachment", attachmentId));
    mediaStorageService.deleteByUrl(att.getImageUrl());
    mediaAttachmentRepository.delete(att);

    OrganizationResponse response = organizationMapper.toResponse(organization);
    enrichWithMedia(response, organizationId);
    return response;
  }

  private static final Set<String> DOCUMENT_TYPES =
      Set.of("BUSINESS_REGISTRATION", "LICENSE", "VAT_REGISTRATION", "TIN_REGISTRATION");
  private static final long MAX_DOCUMENT_SIZE = 20 * 1024 * 1024; // 20MB

  @Override
  public OrganizationResponse uploadOrganizationDocument(
      UUID organizationId, String documentType, MultipartFile file) {
    if (documentType == null || !DOCUMENT_TYPES.contains(documentType.toUpperCase())) {
      throw new BusinessException(
          "Invalid documentType. Must be one of: BUSINESS_REGISTRATION, LICENSE, VAT_REGISTRATION,"
              + " TIN_REGISTRATION");
    }
    String type = documentType.toUpperCase();

    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));

    if (!UserContext.isAdmin()) {
      UUID currentUserId = UserContext.getCurrentUserId();
      boolean isPrimaryContact =
          organization.getPrimaryContact() != null
              && organization.getPrimaryContact().getId().equals(currentUserId);
      if (!isPrimaryContact) {
        RealEstateAgent agent = realEstateAgentRepository.findByUserId(currentUserId).orElse(null);
        boolean isSuperAgent =
            agent != null
                && Boolean.TRUE.equals(agent.getIsSuperAgent())
                && organization.getId().equals(agent.getOrganizationId());
        if (!isSuperAgent) {
          throw new BusinessException(
              "Only admin or organization primary contact can upload organization documents");
        }
      }
    }

    if (file == null || file.isEmpty()) {
      throw new BusinessException("File is required");
    }
    if (file.getSize() > MAX_DOCUMENT_SIZE) {
      throw new BusinessException(
          "File " + file.getOriginalFilename() + " exceeds maximum size of 20MB");
    }
    String contentType = file.getContentType();
    if (contentType == null) {
      contentType = "application/octet-stream";
    }
    boolean allowed =
        contentType.equals("application/pdf")
            || contentType.startsWith("image/")
            || contentType.equals("application/msword")
            || contentType.equals(
                "application/vnd.openxmlformats-officedocument.wordprocessorml.document");
    if (!allowed) {
      throw new BusinessException("File must be PDF, image, or Word document. Got: " + contentType);
    }

    String subPath = "organizations/" + organizationId + "/documents/" + type;
    String oldUrl = getDocumentUrlForType(organization, type);
    if (oldUrl != null && mediaStorageService.isUploadsUrl(oldUrl)) {
      mediaStorageService.deleteByUrl(oldUrl);
    }

    String newUrl = mediaStorageService.save(file, subPath);
    setDocumentUrlForType(organization, type, newUrl);
    organizationRepository.save(organization);
    evictAvailablePropertiesByOrgCache(organizationId);

    OrganizationResponse response = organizationMapper.toResponse(organization);
    enrichWithMedia(response, organizationId);
    return response;
  }

  @Override
  public OrganizationResponse updateOrganizationSupplierSubcategories(
      UUID organizationId, SupplierSubcategoryIdsRequest request) {
    Organization organization =
        organizationRepository
            .findByIdWithSupplierSubcategories(organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
    if (organization.getType() != Organization.OrganizationType.SUPPLIER) {
      throw new BusinessException("Supplier subcategories apply only to SUPPLIER organizations");
    }
    assertCanUpdateSupplierSubcategoryAssignments(organization);
    List<UUID> ids =
        request.getSupplierSubcategoryIds() != null
            ? request.getSupplierSubcategoryIds()
            : List.of();
    syncSupplierSubcategories(organization, ids);
    Organization updated = organizationRepository.save(organization);
    evictAvailablePropertiesByOrgCache(organizationId);
    OrganizationResponse response = organizationMapper.toResponse(updated);
    enrichWithMedia(response, organizationId);
    return response;
  }

  private void assertCanUpdateSupplierSubcategoryAssignments(Organization organization) {
    if (UserContext.isAdmin()) {
      return;
    }
    UUID currentUserId = UserContext.getCurrentUserId();
    if (organization.getPrimaryContact() != null
        && organization.getPrimaryContact().getId().equals(currentUserId)) {
      return;
    }
    throw new BusinessException(
        "Only admin or the supplier organization's primary contact can update material"
            + " subcategories");
  }

  private void syncSupplierSubcategories(Organization organization, List<UUID> ids) {
    if (ids == null) {
      return;
    }
    if (organization.getType() != Organization.OrganizationType.SUPPLIER) {
      if (!ids.isEmpty()) {
        throw new BusinessException("Supplier subcategories apply only to SUPPLIER organizations");
      }
      return;
    }
    Set<SupplierSubcategory> next = new HashSet<>();
    if (!ids.isEmpty()) {
      List<SupplierSubcategory> found = supplierSubcategoryRepository.findAllById(ids);
      if (found.size() != ids.size()) {
        throw new BusinessException("One or more supplier subcategories were not found");
      }
      for (SupplierSubcategory s : found) {
        if (!s.isActive()) {
          throw new BusinessException("Cannot assign inactive subcategory: " + s.getName());
        }
        next.add(s);
      }
    }
    organization.getSupplierSubcategories().clear();
    organization.getSupplierSubcategories().addAll(next);
  }

  private String getDocumentUrlForType(Organization org, String documentType) {
    switch (documentType) {
      case "BUSINESS_REGISTRATION":
        return org.getBusinessRegistration();
      case "LICENSE":
        return org.getLicense();
      case "VAT_REGISTRATION":
        return org.getVatRegistration();
      case "TIN_REGISTRATION":
        return org.getTinRegistration();
      default:
        return null;
    }
  }

  private void setDocumentUrlForType(Organization org, String documentType, String url) {
    switch (documentType) {
      case "BUSINESS_REGISTRATION":
        org.setBusinessRegistration(url);
        break;
      case "LICENSE":
        org.setLicense(url);
        break;
      case "VAT_REGISTRATION":
        org.setVatRegistration(url);
        break;
      case "TIN_REGISTRATION":
        org.setTinRegistration(url);
        break;
      default:
        break;
    }
  }

  /** Evicts cached property list for this org so verified badge and company info stay fresh. */
  private void evictAvailablePropertiesByOrgCache(UUID organizationId) {
    var cache = cacheManager.getCache("availablePropertiesByOrg");
    if (cache != null) {
      cache.evict(organizationId);
    }
  }

  /**
   * Evicts all sponsorship-related caches. Must be called whenever an organization's status changes
   * in a way that affects sponsorship list visibility (suspend / reactivate).
   */
  private void evictSponsorshipCaches() {
    var sponsored = cacheManager.getCache("sponsoredOrganizations");
    if (sponsored != null) {
      sponsored.clear();
    }
    var exclusive = cacheManager.getCache("exclusiveOrganizations");
    if (exclusive != null) {
      exclusive.clear();
    }
  }
}
