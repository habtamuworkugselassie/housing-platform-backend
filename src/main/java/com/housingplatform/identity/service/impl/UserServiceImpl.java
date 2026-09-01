package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationRoles;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.UserCreateRequest;
import com.housingplatform.identity.dto.UserResponse;
import com.housingplatform.identity.dto.UserUpdateRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.UserMapper;
import com.housingplatform.identity.service.UserService;
import com.housingplatform.media.domain.MediaAttachment;
import com.housingplatform.media.repository.MediaAttachmentRepository;
import com.housingplatform.media.service.MediaStorageService;
import com.housingplatform.media.util.UserProfileMediaUrls;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final RealEstateAgentRepository realEstateAgentRepository;
  private final MediaAttachmentRepository mediaAttachmentRepository;
  private final MediaStorageService mediaStorageService;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional(readOnly = true)
  public UserResponse getUserById(UUID id) {
    User user =
        userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    return populateProfileImage(userMapper.toResponse(user), user);
  }

  @Override
  @Transactional(readOnly = true)
  public UserResponse getCurrentUser(UUID userId) {
    return getUserById(userId);
  }

  @Override
  @Transactional(readOnly = true)
  public UserResponse getCurrentUserFromContext() {
    UUID userId = UserContext.getCurrentUserId();
    return getUserById(userId);
  }

  @Override
  public void deleteOwnAccount() {
    UUID userId = UserContext.getCurrentUserId();
    User user =
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    if (user.getStatus() == User.UserStatus.DELETED) {
      return; // already deleted — idempotent
    }
    // Admins and super-admins may delete their own account. The one exception:
    // the last remaining super-admin can't delete themselves, or nobody could
    // administer the platform. Assign another super-admin first.
    if (user.getRoles() != null && user.getRoles().contains(User.UserRole.SUPER_ADMIN)) {
      long otherSuperAdmins =
          userRepository.countByRoleAndStatusExcludingId(
              User.UserRole.SUPER_ADMIN, User.UserStatus.ACTIVE, userId);
      if (otherSuperAdmins == 0) {
        throw new BusinessException(
            "You are the last super-admin. Assign another super-admin before deleting your account.");
      }
    }
    // Irreversibly scrub personal data and close the account.
    user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
    user.setFirstName("Deleted");
    user.setLastName("User");
    user.setPhoneNumber(null);
    user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
    user.setEmailVerified(false);
    user.setPhoneVerified(false);
    if (user.getRoles() != null) {
      user.getRoles().clear();
    }
    user.setOrganization(null);
    user.setStatus(User.UserStatus.DELETED);
    userRepository.save(user);
  }

  private UserResponse populateProfileImage(UserResponse response, User user) {
    List<MediaAttachment> attachments =
        mediaAttachmentRepository.findByUserIdOrderByDisplayOrderAsc(user.getId());
    if (!attachments.isEmpty()) {
      for (MediaAttachment att : attachments) {
        String url = UserProfileMediaUrls.profileImageUrl(att, user.getId());
        if (url != null && !url.isBlank()) {
          response.setProfileImageUrl(url);
          break;
        }
      }
    }
    return response;
  }

  @Override
  public UserResponse createUser(UserCreateRequest request) {
    requireSuperAdminForPrivilegedRoles(request.getRoles(), Set.of());
    if (userRepository.findByEmail(request.getEmail().trim().toLowerCase()).isPresent()) {
      throw new BusinessException("Email already registered");
    }
    if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
      if (userRepository.findByPhoneNumber(request.getPhoneNumber().trim()).isPresent()) {
        throw new BusinessException("Phone number already registered");
      }
    }
    com.housingplatform.identity.domain.Organization organization = null;
    if (request.getOrganizationId() != null) {
      organization =
          organizationRepository
              .findById(request.getOrganizationId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Organization", request.getOrganizationId()));
    }

    User user =
        User.builder()
            .email(request.getEmail().toLowerCase().trim())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
            .status(User.UserStatus.ACTIVE)
            .emailVerified(false)
            .phoneVerified(false)
            .roles(request.getRoles())
            .organization(organization)
            .build();
    User saved = userRepository.save(user);
    return userMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<UserResponse> getAllUsers(
      String search, String role, String status, Pageable pageable) {
    Specification<User> spec = Specification.where(null);

    // Apply search filter
    if (search != null && !search.trim().isEmpty()) {
      String searchLower = search.trim().toLowerCase();
      Specification<User> searchSpec =
          (root, query, cb) ->
              cb.or(
                  cb.like(cb.lower(root.get("email")), "%" + searchLower + "%"),
                  cb.like(cb.lower(root.get("firstName")), "%" + searchLower + "%"),
                  cb.like(cb.lower(root.get("lastName")), "%" + searchLower + "%"),
                  cb.like(cb.lower(root.get("phoneNumber")), "%" + searchLower + "%"));
      spec = spec.and(searchSpec);
    }

    // Apply role filter
    if (role != null && !role.trim().isEmpty()) {
      try {
        User.UserRole userRole = User.UserRole.valueOf(role.toUpperCase());
        Specification<User> roleSpec =
            (root, query, cb) -> {
              query.distinct(true);
              return cb.isMember(userRole, root.get("roles"));
            };
        spec = spec.and(roleSpec);
      } catch (IllegalArgumentException e) {
        // Invalid role, ignore filter
      }
    }

    // Apply status filter
    if (status != null && !status.trim().isEmpty()) {
      try {
        // Map frontend status values to backend enum values
        String statusUpper = status.toUpperCase();
        User.UserStatus userStatus;
        if ("ENABLED".equals(statusUpper) || "ACTIVE".equals(statusUpper)) {
          userStatus = User.UserStatus.ACTIVE;
        } else if ("DISABLED".equals(statusUpper) || "INACTIVE".equals(statusUpper)) {
          userStatus = User.UserStatus.INACTIVE;
        } else {
          userStatus = User.UserStatus.valueOf(statusUpper);
        }
        Specification<User> statusSpec =
            (root, query, cb) -> cb.equal(root.get("status"), userStatus);
        spec = spec.and(statusSpec);
      } catch (IllegalArgumentException e) {
        // Invalid status, ignore filter
      }
    }

    Page<User> users = userRepository.findAll(spec, pageable);
    return users.map(user -> populateProfileImage(userMapper.toResponse(user), user));
  }

  @Override
  @CacheEvict(value = "users", key = "#id")
  public UserResponse updateUser(UUID id, UserUpdateRequest request) {
    User user =
        userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));

    if (UserContext.isAdmin() && request.getRoles() != null && request.getRoles().isEmpty()) {
      throw new BusinessException("At least one role is required");
    }

    if (UserContext.isAdmin() && request.getRoles() != null) {
      requireSuperAdminForPrivilegedRoles(request.getRoles(), user.getRoles());
    }
    guardAgainstSelfLockout(user, request);

    // Admins can update any user
    // Only admins can update roles and status
    // Do not call user.setRoles(request.getRoles()) here: that replaces the Hibernate-managed
    // ElementCollection instance and role changes may not persist. Roles are merged in
    // userMapper.updateEntity via clear() + addAll() on the existing collection.
    if (UserContext.isAdmin()) {
      if (request.getStatus() != null) {
        user.setStatus(request.getStatus());
      }

      // Handle enabled field (map to status for backward compatibility)
      if (request.getEnabled() != null) {
        if (request.getEnabled()) {
          user.setStatus(User.UserStatus.ACTIVE);
        } else {
          user.setStatus(User.UserStatus.INACTIVE);
        }
      }
    }

    userMapper.updateEntity(user, request);

    if (UserContext.isAdmin()) {
      if (request.getOrganizationId() != null) {
        Organization org =
            organizationRepository
                .findById(request.getOrganizationId())
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException("Organization", request.getOrganizationId()));
        validateOrganizationAssignable(org, user.getRoles());
        user.setOrganization(org);
        syncRealEstateAgentLink(user, org);
      } else if (Boolean.TRUE.equals(request.getClearOrganization())) {
        user.setOrganization(null);
        syncRealEstateAgentLink(user, null);
      }
    }

    User updated = userRepository.save(user);
    return populateProfileImage(userMapper.toResponse(updated), updated);
  }

  /**
   * Only a super admin may hand out or take away {@code ADMIN} / {@code SUPER_ADMIN}. Without this
   * any admin could mint another admin (or promote themselves), which would make the super-admin
   * tier decorative.
   *
   * @param requested roles the caller wants the account to end up with
   * @param current roles the account holds today (empty for a create)
   */
  private static void requireSuperAdminForPrivilegedRoles(
      Set<User.UserRole> requested, Set<User.UserRole> current) {
    if (requested == null || UserContext.isSuperAdmin()) {
      return;
    }
    for (User.UserRole role : User.UserRole.values()) {
      if (!role.isPrivileged()) {
        continue;
      }
      boolean granting = requested.contains(role) && !current.contains(role);
      boolean revoking = !requested.contains(role) && current.contains(role);
      if (granting || revoking) {
        throw new BusinessException(
            "Only a super admin can grant or revoke the " + role + " role.");
      }
    }
  }

  /**
   * Stops the last hand on the wheel from removing its own access: a super admin cannot strip their
   * own privileged roles or deactivate their own account, since nobody else could restore them.
   */
  private static void guardAgainstSelfLockout(User target, UserUpdateRequest request) {
    if (!UserContext.isSuperAdmin() || !target.getId().equals(UserContext.getCurrentUserId())) {
      return;
    }
    if (request.getRoles() != null
        && !request.getRoles().contains(User.UserRole.SUPER_ADMIN)
        && target.getRoles().contains(User.UserRole.SUPER_ADMIN)) {
      throw new BusinessException(
          "You cannot remove your own super admin role. Ask another super admin to do it.");
    }
    boolean deactivating =
        Boolean.FALSE.equals(request.getEnabled())
            || (request.getStatus() != null && request.getStatus() != User.UserStatus.ACTIVE);
    if (deactivating) {
      throw new BusinessException("You cannot deactivate your own super admin account.");
    }
  }

  private static void validateOrganizationAssignable(Organization org, Set<User.UserRole> roles) {
    if (!OrganizationRoles.matchesPortalRole(org.getType(), roles)) {
      throw new BusinessException(
          String.format(
              "Organization type %s requires the %s role on the user.",
              org.getType(), OrganizationRoles.portalRoleFor(org.getType())));
    }
  }

  private void syncRealEstateAgentLink(User user, Organization newOrg) {
    Optional<RealEstateAgent> existing = realEstateAgentRepository.findByUserId(user.getId());
    boolean needsAgent =
        user.getRoles().contains(User.UserRole.REALTOR)
            && newOrg != null
            && newOrg.getType() == Organization.OrganizationType.REAL_ESTATE_COMPANY;
    if (!needsAgent) {
      existing.ifPresent(realEstateAgentRepository::delete);
      return;
    }
    if (existing.isPresent()) {
      RealEstateAgent agent = existing.get();
      agent.setOrganization(newOrg);
      realEstateAgentRepository.save(agent);
    } else {
      realEstateAgentRepository.save(
          RealEstateAgent.builder()
              .user(user)
              .organization(newOrg)
              .status(RealEstateAgent.AgentStatus.ACTIVE)
              .isSuperAgent(false)
              .build());
    }
  }

  @Override
  @Transactional
  public UserResponse uploadProfileImage(UUID userId, MultipartFile file) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

    List<MediaAttachment> existing =
        mediaAttachmentRepository.findByUserIdOrderByDisplayOrderAsc(userId);
    for (MediaAttachment att : existing) {
      mediaStorageService.deleteByUrl(att.getImageUrl());
    }
    mediaAttachmentRepository.deleteAll(existing);

    String imageUrl = mediaStorageService.save(file, "users/" + userId);
    MediaAttachment attachment =
        MediaAttachment.builder()
            .user(user)
            .fileName(file.getOriginalFilename())
            .contentType(file.getContentType())
            .imageUrl(imageUrl)
            .displayOrder(0)
            .isPrimary(true)
            .mediaKind(MediaAttachment.MediaKind.IMAGE)
            .build();

    mediaAttachmentRepository.save(attachment);

    return getUserById(userId);
  }

  @Override
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> getUserProfileImage(UUID userId, UUID attachmentId) {
    MediaAttachment attachment =
        mediaAttachmentRepository
            .findByIdAndUserId(attachmentId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("MediaAttachment", attachmentId));

    String imageUrl = attachment.getImageUrl();
    if (mediaStorageService.isUploadsUrl(imageUrl)) {
      try (var in = mediaStorageService.getInputStream(imageUrl)) {
        byte[] body = in.readAllBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
            attachment.getContentType() != null
                ? MediaType.parseMediaType(attachment.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
      } catch (IOException e) {
        throw new ResourceNotFoundException("Profile image file not found on disk");
      }
    }

    if (!attachment.hasFileData()) {
      throw new BusinessException("No file data found for this attachment");
    }

    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_TYPE,
            attachment.getContentType() != null
                ? attachment.getContentType()
                : "application/octet-stream")
        .body(attachment.getFileData());
  }
}
