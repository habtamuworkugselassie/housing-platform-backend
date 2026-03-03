package com.housingplatform.property.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.SponsorshipApplication;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.SponsorshipApplicationRepository;
import com.housingplatform.identity.service.RealEstateAgentService;
import com.housingplatform.media.domain.MediaAttachment;
import com.housingplatform.media.repository.MediaAttachmentRepository;
import com.housingplatform.media.service.MediaStorageService;
import com.housingplatform.property.domain.Building;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.dto.PropertyRequest;
import com.housingplatform.property.dto.PropertyResponse;
import com.housingplatform.property.repository.BuildingRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class PropertyServiceImpl implements PropertyService {

  private final PropertyRepository propertyRepository;
  private final BuildingRepository buildingRepository;
  private final PropertyMapper propertyMapper;
  private final RealEstateAgentService agentService;
  private final OrganizationRepository organizationRepository;
  private final SponsorshipApplicationRepository sponsorshipApplicationRepository;
  private final MediaAttachmentRepository mediaAttachmentRepository;
  private final MediaStorageService mediaStorageService;

  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

  @Override
  public PropertyResponse createProperty(PropertyRequest request, UUID agentId) {
    // Validate required fields for create operation
    if (request.getRealEstateCompanyId() == null) {
      throw new BusinessException("Real estate company ID is required");
    }
    if (request.getCategory() == null) {
      throw new BusinessException("Category is required");
    }
    if (request.getConstructionStatus() == null) {
      throw new BusinessException("Construction status is required");
    }

    // Validate agent can manage properties for this company (skip if admin)
    if (agentId != null && !UserContext.isAdmin()) {
      agentService.validateAgentCanManageProperty(agentId, request.getRealEstateCompanyId());
    }

    Property property = propertyMapper.toEntity(request);

    property.setAgentId(agentId);
    property.setStatus(Property.PropertyStatus.AVAILABLE);
    property.setVerificationStatus(Property.VerificationStatus.PENDING);

    // Set building relationship if provided
    if (request.getBuildingId() != null) {
      Building building =
          buildingRepository
              .findById(request.getBuildingId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Building", request.getBuildingId()));
      property.setBuilding(building);
      property.setUnitNumber(request.getUnitNumber());
    }

    Property saved = propertyRepository.save(property);
    return propertyMapper.toResponseWithImages(saved);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "properties", key = "#id")
  public PropertyResponse getPropertyById(UUID id) {
    Property property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));

    PropertyResponse response = propertyMapper.toResponseWithImages(property);

    // Enrich with sponsorship info
    if (property.getRealEstateCompanyId() != null) {
      // Fetch organization
      Organization org =
          organizationRepository.findById(property.getRealEstateCompanyId()).orElse(null);
      Map<UUID, Organization> organizationsMap =
          org != null ? java.util.Map.of(org.getId(), org) : java.util.Collections.emptyMap();

      // Fetch active sponsorship application for this organization
      List<SponsorshipApplication> activeApplications =
          sponsorshipApplicationRepository
              .findAllActiveApplications(java.time.LocalDateTime.now())
              .stream()
              .filter(
                  app -> app.getOrganization().getId().equals(property.getRealEstateCompanyId()))
              .collect(Collectors.toList());

      Map<UUID, SponsorshipApplication> applicationMap =
          activeApplications.stream()
              .collect(
                  Collectors.toMap(
                      app -> app.getOrganization().getId(),
                      Function.identity(),
                      (existing, replacement) -> {
                        // If multiple active applications exist, prefer PREMIER over BASIC
                        if (replacement.getSponsorship().getType()
                            == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                                .PREMIER) {
                          return replacement;
                        }
                        return existing;
                      }));

      enrichWithSponsorshipInfo(response, property, organizationsMap, applicationMap);
    }

    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PropertyResponse> getAllProperties(
      String status, String city, Pageable pageable, boolean publicOnly) {
    Specification<Property> spec = Specification.where(null);

    // For public access, only show AVAILABLE properties (ignore status parameter for security)
    // For authenticated users, respect the status parameter
    if (publicOnly) {
      spec =
          spec.and(
              (root, query, cb) -> cb.equal(root.get("status"), Property.PropertyStatus.AVAILABLE));
    } else if (status != null && !status.trim().isEmpty()) {
      try {
        Property.PropertyStatus statusEnum = Property.PropertyStatus.valueOf(status.toUpperCase());
        spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), statusEnum));
      } catch (IllegalArgumentException e) {
        // Invalid status value, ignore it
      }
    }

    if (city != null) {
      spec =
          spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("city")), city.toLowerCase()));
    }

    // Get all properties matching the criteria
    List<Property> allProperties = propertyRepository.findAll(spec);

    // Fetch all organizations for company name (optimized: single query instead of N+1)
    Set<UUID> organizationIds =
        allProperties.stream()
            .map(Property::getRealEstateCompanyId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, Organization> organizationsMap =
        organizationIds.isEmpty()
            ? java.util.Collections.emptyMap()
            : organizationRepository.findAllById(organizationIds).stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));

    // For public access, hide properties belonging to suspended organizations
    if (publicOnly && !organizationsMap.isEmpty()) {
      allProperties =
          allProperties.stream()
              .filter(
                  p -> {
                    Organization org = organizationsMap.get(p.getRealEstateCompanyId());
                    return org == null
                        || org.getStatus() != Organization.OrganizationStatus.SUSPENDED;
                  })
              .collect(Collectors.toList());
    }

    // Fetch all active sponsorship applications
    List<SponsorshipApplication> activeApplications =
        sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
    Map<UUID, SponsorshipApplication> applicationMap =
        activeApplications.stream()
            .collect(
                Collectors.toMap(
                    app -> app.getOrganization().getId(),
                    Function.identity(),
                    (existing, replacement) -> {
                      // If multiple active applications exist, prefer PREMIER over BASIC
                      if (replacement.getSponsorship().getType()
                          == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                              .PREMIER) {
                        return replacement;
                      }
                      return existing;
                    }));

    // Sort by sponsorship priority: Premier (0) > Basic (1) > None (2)
    // Then by creation date (newest first)
    // This ensures sponsored properties always appear at the top of every page
    List<Property> sortedProperties =
        allProperties.stream()
            .sorted(
                Comparator.comparing(
                        (Property p) -> {
                          SponsorshipApplication application =
                              applicationMap.get(p.getRealEstateCompanyId());
                          if (application != null && application.isActive()) {
                            // Premier gets highest priority (0), Basic gets second priority (1)
                            return application.getSponsorship().getType()
                                    == com.housingplatform.identity.domain.Sponsorship
                                        .SponsorshipType.PREMIER
                                ? 0
                                : 1;
                          }
                          return 2; // Non-sponsored properties get lowest priority
                        })
                    .thenComparing(Property::getCreatedAt, Comparator.reverseOrder()))
            .collect(Collectors.toList());

    // Apply pagination manually
    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), sortedProperties.size());
    List<Property> pagedProperties = sortedProperties.subList(start, end);

    // Map to response and enrich with sponsorship info
    List<PropertyResponse> responses =
        pagedProperties.stream()
            .map(
                property -> {
                  PropertyResponse response = propertyMapper.toResponseWithImages(property);
                  enrichWithSponsorshipInfo(response, property, organizationsMap, applicationMap);
                  return response;
                })
            .collect(Collectors.toList());

    return new org.springframework.data.domain.PageImpl<>(
        responses, pageable, sortedProperties.size());
  }

  private void enrichWithSponsorshipInfo(
      PropertyResponse response,
      Property property,
      Map<UUID, Organization> organizationsMap,
      Map<UUID, SponsorshipApplication> applicationMap) {
    if (property.getRealEstateCompanyId() != null) {
      Organization org = organizationsMap.get(property.getRealEstateCompanyId());
      if (org != null) {
        response.setRealEstateCompanyName(org.getName());
        if (org.getPhones() != null && !org.getPhones().isEmpty()) {
          org.getPhones().stream()
              .min(
                  Comparator.comparing(
                      com.housingplatform.identity.domain.OrganizationPhone::getDisplayOrder))
              .ifPresent(
                  p ->
                      response.setRealEstateCompanyPhone(
                          (p.getCountryCode() != null ? p.getCountryCode() : "").trim()
                              + (p.getNumber() != null ? " " + p.getNumber().trim() : "")));
        }
      }

      SponsorshipApplication application = applicationMap.get(property.getRealEstateCompanyId());
      if (application != null && application.isActive()) {
        response.setIsSponsored(true);
        response.setSponsorshipType(application.getSponsorship().getType().name());
      } else {
        response.setIsSponsored(false);
      }
    }
  }

  @Override
  @CacheEvict(value = "properties", key = "#id")
  public PropertyResponse updateProperty(UUID id, PropertyRequest request, UUID agentId) {
    Property property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));

    // Validate agent can manage this property (skip if admin)
    if (agentId != null && !UserContext.isAdmin()) {
      com.housingplatform.identity.dto.AgentResponse agent = agentService.getAgentById(agentId);

      // Check if agent is super agent of the organization that owns the property
      boolean isSuperAgent =
          Boolean.TRUE.equals(agent.getIsSuperAgent())
              && agent.getOrganizationId().equals(property.getRealEstateCompanyId());

      // Allow if: agent owns the property OR agent is super agent of the organization
      if (!property.getAgentId().equals(agentId) && !isSuperAgent) {
        throw new BusinessException(
            "Agent can only update properties they manage or if they are super agent of the organization");
      }

      // Validate the property belongs to the agent's organization
      if (!agent.getOrganizationId().equals(property.getRealEstateCompanyId())) {
        throw new BusinessException("Property does not belong to your organization");
      }

      // Only validate agent can manage property if not super agent (super agents can manage all org
      // properties)
      if (!isSuperAgent && request.getRealEstateCompanyId() != null) {
        agentService.validateAgentCanManageProperty(agentId, request.getRealEstateCompanyId());
      }
    }

    propertyMapper.updateEntity(property, request);
    Property updated = propertyRepository.save(property);
    return propertyMapper.toResponseWithImages(updated);
  }

  @Override
  @CacheEvict(value = "properties", key = "#id")
  public void deleteProperty(UUID id, UUID agentId) {
    Property property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));

    // Validate agent can manage this property (skip if admin)
    if (agentId != null && !UserContext.isAdmin()) {
      com.housingplatform.identity.dto.AgentResponse agent = agentService.getAgentById(agentId);

      // Check if agent is super agent of the organization that owns the property
      boolean isSuperAgent =
          Boolean.TRUE.equals(agent.getIsSuperAgent())
              && agent.getOrganizationId().equals(property.getRealEstateCompanyId());

      // Allow if: agent owns the property OR agent is super agent of the organization
      if (!property.getAgentId().equals(agentId) && !isSuperAgent) {
        throw new BusinessException(
            "Agent can only delete properties they manage or if they are super agent of the organization");
      }

      // Validate the property belongs to the agent's organization
      if (!agent.getOrganizationId().equals(property.getRealEstateCompanyId())) {
        throw new BusinessException("Property does not belong to your organization");
      }
    }

    propertyRepository.delete(property);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PropertyResponse> getPropertiesByCompanyId(UUID companyId) {
    return propertyRepository.findByRealEstateCompanyId(companyId).stream()
        .map(propertyMapper::toResponseWithImages)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<PropertyResponse> getAvailablePropertiesByCompanyIdForMarketplace(UUID companyId) {
    List<Property> properties =
        propertyRepository.findByRealEstateCompanyId(companyId).stream()
            .filter(p -> p.getStatus() == Property.PropertyStatus.AVAILABLE)
            .collect(Collectors.toList());
    if (properties.isEmpty()) {
      return List.of();
    }
    Set<UUID> organizationIds =
        properties.stream()
            .map(Property::getRealEstateCompanyId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, Organization> organizationsMap =
        organizationIds.isEmpty()
            ? java.util.Collections.emptyMap()
            : organizationRepository.findAllById(organizationIds).stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));
    List<SponsorshipApplication> activeApplications =
        sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
    Map<UUID, SponsorshipApplication> applicationMap =
        activeApplications.stream()
            .collect(
                Collectors.toMap(
                    app -> app.getOrganization().getId(),
                    Function.identity(),
                    (existing, replacement) -> {
                      if (replacement.getSponsorship().getType()
                          == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                              .PREMIER) {
                        return replacement;
                      }
                      return existing;
                    }));
    return properties.stream()
        .map(
            p -> {
              PropertyResponse response = propertyMapper.toResponseWithImages(p);
              enrichWithSponsorshipInfo(response, p, organizationsMap, applicationMap);
              return response;
            })
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<PropertyResponse> getPropertiesByAgentId(UUID agentId) {
    return propertyRepository.findByAgentId(agentId).stream()
        .map(propertyMapper::toResponseWithImages)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<PropertyResponse> searchProperties(
      String companyName, String city, String state, String country, String title, Integer limit) {
    Specification<Property> spec = Specification.where(null);

    // Only return AVAILABLE properties for public search
    spec =
        spec.and(
            (root, query, cb) -> cb.equal(root.get("status"), Property.PropertyStatus.AVAILABLE));

    // Search by title (partial match)
    if (title != null && !title.trim().isEmpty()) {
      String titleLower = title.toLowerCase();
      spec =
          spec.and(
              (root, query, cb) -> cb.like(cb.lower(root.get("title")), "%" + titleLower + "%"));
    }

    // Search by city
    if (city != null && !city.trim().isEmpty()) {
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.like(cb.lower(root.get("city")), "%" + city.toLowerCase() + "%"));
    }

    // Search by state
    if (state != null && !state.trim().isEmpty()) {
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.like(cb.lower(root.get("state")), "%" + state.toLowerCase() + "%"));
    }

    // Search by country
    if (country != null && !country.trim().isEmpty()) {
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.like(cb.lower(root.get("country")), "%" + country.toLowerCase() + "%"));
    }

    // Get all matching properties
    List<Property> properties = propertyRepository.findAll(spec);

    // Filter by company name if provided
    if (companyName != null && !companyName.trim().isEmpty()) {
      String companyNameLower = companyName.toLowerCase();
      List<UUID> matchingCompanyIds =
          organizationRepository.findAll().stream()
              .filter(org -> org.getName().toLowerCase().contains(companyNameLower))
              .map(Organization::getId)
              .collect(Collectors.toList());

      properties =
          properties.stream()
              .filter(
                  p ->
                      p.getRealEstateCompanyId() != null
                          && matchingCompanyIds.contains(p.getRealEstateCompanyId()))
              .collect(Collectors.toList());
    }

    // Limit results
    if (limit != null && limit > 0) {
      properties = properties.stream().limit(limit).collect(Collectors.toList());
    }

    // Fetch organizations for company names (optimized: single query instead of N+1)
    Set<UUID> organizationIds =
        properties.stream()
            .map(Property::getRealEstateCompanyId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, Organization> organizationsMap =
        organizationIds.isEmpty()
            ? java.util.Collections.emptyMap()
            : organizationRepository.findAllById(organizationIds).stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));

    // Fetch all active sponsorship applications
    List<SponsorshipApplication> activeApplications =
        sponsorshipApplicationRepository.findAllActiveApplications(java.time.LocalDateTime.now());
    Map<UUID, SponsorshipApplication> applicationMap =
        activeApplications.stream()
            .collect(
                Collectors.toMap(
                    app -> app.getOrganization().getId(),
                    Function.identity(),
                    (existing, replacement) -> {
                      // If multiple active applications exist, prefer PREMIER over BASIC
                      if (replacement.getSponsorship().getType()
                          == com.housingplatform.identity.domain.Sponsorship.SponsorshipType
                              .PREMIER) {
                        return replacement;
                      }
                      return existing;
                    }));

    // Sort by sponsorship priority: Premier > Basic > None, then by creation date (newest first)
    List<Property> sortedProperties =
        properties.stream()
            .sorted(
                Comparator.comparing(
                        (Property p) -> {
                          SponsorshipApplication application =
                              applicationMap.get(p.getRealEstateCompanyId());
                          if (application != null && application.isActive()) {
                            return application.getSponsorship().getType()
                                    == com.housingplatform.identity.domain.Sponsorship
                                        .SponsorshipType.PREMIER
                                ? 0
                                : 1;
                          }
                          return 2; // Non-sponsored properties get lowest priority
                        })
                    .thenComparing(Property::getCreatedAt, Comparator.reverseOrder()))
            .collect(Collectors.toList());

    // Exclude properties from suspended organizations (public search)
    List<Property> visibleProperties =
        sortedProperties.stream()
            .filter(
                p -> {
                  Organization org = organizationsMap.get(p.getRealEstateCompanyId());
                  return org == null
                      || org.getStatus() != Organization.OrganizationStatus.SUSPENDED;
                })
            .collect(Collectors.toList());

    // Map to response and enrich with company names and sponsorship info
    return visibleProperties.stream()
        .map(
            property -> {
              PropertyResponse response = propertyMapper.toResponseWithImages(property);
              enrichWithSponsorshipInfo(response, property, organizationsMap, applicationMap);
              return response;
            })
        .collect(Collectors.toList());
  }

  @Override
  @CacheEvict(value = "properties", key = "#id")
  public PropertyResponse uploadPropertyMedia(
      UUID id, List<MultipartFile> files, List<String> captions, UUID agentId) {
    Property property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));

    // Validate agent can manage this property (skip if admin)
    if (agentId != null && !UserContext.isAdmin()) {
      com.housingplatform.identity.dto.AgentResponse agent = agentService.getAgentById(agentId);
      boolean isSuperAgent =
          Boolean.TRUE.equals(agent.getIsSuperAgent())
              && agent.getOrganizationId().equals(property.getRealEstateCompanyId());

      if (!property.getAgentId().equals(agentId) && !isSuperAgent) {
        throw new BusinessException(
            "Agent can only update properties they manage or if they are super agent of the organization");
      }
    }

    if (files == null || files.isEmpty()) {
      throw new BusinessException("At least one file must be provided");
    }

    // Get current max display order
    List<MediaAttachment> existingImages =
        mediaAttachmentRepository.findByPropertyIdOrderByDisplayOrderAsc(id);
    int nextDisplayOrder =
        existingImages.isEmpty()
            ? 0
            : existingImages.stream().mapToInt(MediaAttachment::getDisplayOrder).max().orElse(0)
                + 1;

    List<MediaAttachment> newImages = new ArrayList<>();

    for (int i = 0; i < files.size(); i++) {
      MultipartFile file = files.get(i);

      if (file.isEmpty()) {
        continue;
      }

      // Validate file size
      if (file.getSize() > MAX_FILE_SIZE) {
        throw new BusinessException(
            "File " + file.getOriginalFilename() + " exceeds maximum size of 10MB");
      }

      // Validate file type (images and videos)
      String contentType = file.getContentType();
      if (contentType == null
          || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
        throw new BusinessException(
            "File " + file.getOriginalFilename() + " must be an image or video");
      }

      String caption = (captions != null && i < captions.size()) ? captions.get(i) : null;
      boolean isVideo = contentType != null && contentType.startsWith("video/");

      // Save file on server and store only the URL in DB
      String imageUrl = mediaStorageService.save(file, "properties/" + id);

      MediaAttachment attachment =
          MediaAttachment.builder()
              .property(property)
              .imageUrl(imageUrl)
              .contentType(contentType)
              .fileName(file.getOriginalFilename())
              .caption(caption)
              .displayOrder(nextDisplayOrder + i)
              .isPrimary(
                  existingImages.isEmpty()
                      && i == 0) // First image is primary if no existing images
              .mediaKind(
                  isVideo ? MediaAttachment.MediaKind.VIDEO : MediaAttachment.MediaKind.IMAGE)
              .build();

      newImages.add(attachment);
    }

    // Save all new attachments
    if (!newImages.isEmpty()) {
      mediaAttachmentRepository.saveAll(newImages);
    }

    Property updated = propertyRepository.findById(id).orElse(property);
    return propertyMapper.toResponseWithImages(updated);
  }

  @Override
  @CacheEvict(value = "properties", key = "#id")
  public PropertyResponse deletePropertyImage(UUID id, UUID imageId, UUID agentId) {
    Property property =
        propertyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));

    MediaAttachment image =
        mediaAttachmentRepository
            .findByIdAndPropertyId(imageId, id)
            .orElseThrow(() -> new ResourceNotFoundException("MediaAttachment", imageId));

    // Validate agent can manage this property (skip if admin)
    if (agentId != null && !UserContext.isAdmin()) {
      com.housingplatform.identity.dto.AgentResponse agent = agentService.getAgentById(agentId);
      boolean isSuperAgent =
          Boolean.TRUE.equals(agent.getIsSuperAgent())
              && agent.getOrganizationId().equals(property.getRealEstateCompanyId());

      if (!property.getAgentId().equals(agentId) && !isSuperAgent) {
        throw new BusinessException(
            "Agent can only update properties they manage or if they are super agent of the organization");
      }
    }

    boolean wasPrimary = image.getIsPrimary();
    mediaStorageService.deleteByUrl(image.getImageUrl());
    mediaAttachmentRepository.delete(image);

    // If this was the primary image, set the first remaining image as primary
    if (wasPrimary) {
      List<MediaAttachment> remainingImages =
          mediaAttachmentRepository.findByPropertyIdOrderByDisplayOrderAsc(id);
      if (!remainingImages.isEmpty()) {
        MediaAttachment newPrimary = remainingImages.get(0);
        newPrimary.setIsPrimary(true);
        mediaAttachmentRepository.save(newPrimary);
      }
    }

    Property updated = propertyRepository.findById(id).orElse(property);
    return propertyMapper.toResponseWithImages(updated);
  }

  @Override
  @Transactional(readOnly = true)
  public org.springframework.http.ResponseEntity<byte[]> getPropertyImageFile(
      UUID id, UUID imageId) {
    MediaAttachment image =
        mediaAttachmentRepository
            .findByIdAndPropertyId(imageId, id)
            .orElseThrow(() -> new ResourceNotFoundException("MediaAttachment", imageId));

    String imageUrl = image.getImageUrl();
    if (mediaStorageService.isUploadsUrl(imageUrl)) {
      try (var in = mediaStorageService.getInputStream(imageUrl)) {
        byte[] body = in.readAllBytes();
        String contentType = image.getContentType();
        if (contentType == null || contentType.isEmpty()) {
          contentType = "application/octet-stream";
        }
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType(contentType));
        headers.setContentLength(body.length);
        if (image.getFileName() != null) {
          headers.setContentDispositionFormData("inline", image.getFileName());
        }
        return org.springframework.http.ResponseEntity.ok().headers(headers).body(body);
      } catch (IOException e) {
        throw new ResourceNotFoundException("Image file not found on disk");
      }
    }

    if (!image.hasFileData()) {
      throw new ResourceNotFoundException("Image file data not found");
    }

    String contentType = image.getContentType();
    if (contentType == null || contentType.isEmpty()) {
      contentType = "application/octet-stream";
    }

    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.parseMediaType(contentType));
    headers.setContentLength(image.getFileData().length);
    if (image.getFileName() != null) {
      headers.setContentDispositionFormData("inline", image.getFileName());
    }

    return org.springframework.http.ResponseEntity.ok().headers(headers).body(image.getFileData());
  }

  @Override
  @Transactional(readOnly = true)
  public com.housingplatform.property.dto.FirstPropertyMediaResponse
      getFirstPropertyMediaForOrganization(UUID organizationId) {
    String imageUrl = null;
    String videoUrl = null;
    List<Property> properties = propertyRepository.findByRealEstateCompanyId(organizationId);
    for (Property property : properties) {
      if (imageUrl != null && videoUrl != null) {
        break;
      }
      List<MediaAttachment> attachments =
          mediaAttachmentRepository.findByPropertyIdOrderByDisplayOrderAsc(property.getId());
      for (MediaAttachment att : attachments) {
        String url =
            att.hasFileData()
                ? "/api/v1/properties/" + property.getId() + "/images/" + att.getId() + "/file"
                : att.getImageUrl();
        if (url == null || url.isBlank()) {
          continue;
        }
        if (imageUrl == null && att.getMediaKind() == MediaAttachment.MediaKind.IMAGE) {
          imageUrl = url;
        }
        if (videoUrl == null && att.getMediaKind() == MediaAttachment.MediaKind.VIDEO) {
          videoUrl = url;
        }
        if (imageUrl != null && videoUrl != null) {
          break;
        }
      }
    }
    return com.housingplatform.property.dto.FirstPropertyMediaResponse.builder()
        .imageUrl(imageUrl)
        .videoUrl(videoUrl)
        .build();
  }
}
