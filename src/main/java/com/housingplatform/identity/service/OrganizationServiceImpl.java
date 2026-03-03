package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationPhone;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.SponsorshipApplication;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.AdminOrganizationCreateRequest;
import com.housingplatform.identity.dto.OrganizationMediaItem;
import com.housingplatform.identity.dto.OrganizationPhoneDto;
import com.housingplatform.identity.dto.OrganizationRequest;
import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.SponsorshipApplicationRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.media.domain.MediaAttachment;
import com.housingplatform.media.repository.MediaAttachmentRepository;
import com.housingplatform.media.service.MediaStorageService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationServiceImpl implements OrganizationService {

  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

  private final OrganizationRepository organizationRepository;
  private final OrganizationMapper organizationMapper;
  private final UserRepository userRepository;
  private final RealEstateAgentRepository realEstateAgentRepository;
  private final SponsorshipApplicationRepository sponsorshipApplicationRepository;
  private final MediaAttachmentRepository mediaAttachmentRepository;
  private final MediaStorageService mediaStorageService;

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

    // If a REALTOR is creating a REAL_ESTATE_COMPANY, automatically create a RealEstateAgent
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
          } else {
            throw new BusinessException(
                "User is already registered as a real estate agent for another organization");
          }
        }
      } catch (IllegalStateException e) {
        // User context not available (shouldn't happen in authenticated endpoint, but handle
        // gracefully)
        // This means the organization was created but no agent was linked
      }
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
    req.setType(request.getType());
    req.setAddress(request.getAddress());
    req.setCity(request.getCity());
    req.setCountry(request.getCountry());
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
    req.setDescription(request.getDescription());
    req.setPrimaryContactUserId(request.getPrimaryContactUserId());

    Organization organization = organizationMapper.toEntity(req);
    syncPhonesFromRequest(organization, req.getPhoneNumbers());
    organization.setStatus(
        request.getInitialStatus() != null
            ? request.getInitialStatus()
            : Organization.OrganizationStatus.PENDING_APPROVAL);
    Organization saved = organizationRepository.save(organization);
    OrganizationResponse response = organizationMapper.toResponse(saved);
    enrichWithMedia(response, saved.getId());
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public OrganizationResponse getOrganizationById(UUID id) {
    Organization organization =
        organizationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
    OrganizationResponse response = organizationMapper.toResponse(organization);
    enrichWithMedia(response, id);
    return response;
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

    // Find bank where user is primary contact
    List<Organization> banks =
        organizationRepository.findByType(Organization.OrganizationType.BANK);
    Organization bank =
        banks.stream()
            .filter(
                b ->
                    b.getPrimaryContact() != null
                        && b.getPrimaryContact().getId().equals(currentUserId))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Bank not found for current user. User must be primary contact of a bank organization."));

    OrganizationResponse response = organizationMapper.toResponse(bank);
    enrichWithMedia(response, bank.getId());
    return response;
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
  public List<OrganizationResponse> getApprovedOrganizationsForMarketplace(String types) {
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
        organizationRepository.findByStatus(Organization.OrganizationStatus.APPROVED);
    List<Organization> filtered =
        all.stream().filter(org -> typeSet.contains(org.getType())).collect(Collectors.toList());
    List<OrganizationResponse> responses = new ArrayList<>();
    for (Organization org : filtered) {
      OrganizationResponse resp = organizationMapper.toResponse(org);
      enrichWithMedia(resp, org.getId());
      responses.add(resp);
    }
    return responses;
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
    Organization updated = organizationRepository.save(organization);
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

    // Re-activate (set back to APPROVED) sponsorship applications that were cancelled when org was
    // suspended
    List<SponsorshipApplication> applications =
        sponsorshipApplicationRepository.findByOrganizationId(id);
    for (SponsorshipApplication app : applications) {
      if (app.getStatus() == SponsorshipApplication.ApplicationStatus.CANCELLED) {
        app.setStatus(SponsorshipApplication.ApplicationStatus.APPROVED);
        sponsorshipApplicationRepository.save(app);
      }
    }

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
    List<OrganizationPhone> phones = organization.getPhones();
    if (phones == null) {
      phones = new ArrayList<>();
      organization.setPhones(phones);
    }
    phones.clear();
    if (phoneNumbers != null && !phoneNumbers.isEmpty()) {
      int order = 0;
      for (OrganizationPhoneDto dto : phoneNumbers) {
        String num = dto.getNumber() != null ? dto.getNumber().trim() : "";
        if (!num.isEmpty()) {
          OrganizationPhone phone =
              OrganizationPhone.builder()
                  .organization(organization)
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
              .organization(organization)
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
            "File " + file.getOriginalFilename() + " exceeds maximum size of 10MB");
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
}
