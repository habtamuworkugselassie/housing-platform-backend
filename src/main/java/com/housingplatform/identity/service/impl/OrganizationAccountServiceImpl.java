package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationRoles;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.CreateOrganizationAccountRequest;
import com.housingplatform.identity.dto.OrganizationAccountResponse;
import com.housingplatform.identity.dto.SetAccountPasswordRequest;
import com.housingplatform.identity.dto.UpdateAccountStatusRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.OrganizationAccountService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationAccountServiceImpl implements OrganizationAccountService {

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final RealEstateAgentRepository realEstateAgentRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional(readOnly = true)
  public List<OrganizationAccountResponse> getAccounts(UUID organizationId) {
    Organization org = requireOrganization(organizationId);
    UUID primaryId = org.getPrimaryContact() != null ? org.getPrimaryContact().getId() : null;
    return userRepository.findByOrganizationId(organizationId).stream()
        .map(user -> toResponse(user, org, primaryId))
        // Primary contact first, then oldest account first.
        .sorted(
            Comparator.comparing(OrganizationAccountResponse::getPrimaryContact)
                .reversed()
                .thenComparing(
                    OrganizationAccountResponse::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  @Override
  public OrganizationAccountResponse createAccount(
      UUID organizationId, CreateOrganizationAccountRequest request) {
    Organization org = requireOrganization(organizationId);
    String email = request.getEmail().trim().toLowerCase();

    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(
          "A user with this email already exists. Link that account to the company from User"
              + " management instead of creating a duplicate.");
    }
    String phone = normalizePhone(request.getPhoneNumber());
    if (phone != null && userRepository.existsByPhoneNumber(phone)) {
      throw new BusinessException("Phone number already registered");
    }

    // No primary contact yet means this first account becomes it unless told otherwise.
    boolean makePrimary =
        request.getMakePrimaryContact() != null
            ? request.getMakePrimaryContact()
            : org.getPrimaryContact() == null;

    User user =
        User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName().trim())
            .lastName(request.getLastName().trim())
            .phoneNumber(phone)
            .status(User.UserStatus.ACTIVE)
            .emailVerified(false)
            .phoneVerified(false)
            .roles(OrganizationRoles.defaultRolesFor(org.getType()))
            .organization(org)
            .build();
    user = userRepository.save(user);

    if (makePrimary) {
      org = promoteToPrimaryContact(org, user);
    } else {
      syncRealEstateAgent(org, user, false);
    }

    log.info(
        "Provisioned account {} for organization {} ({})", email, org.getName(), organizationId);
    return toResponse(user, org, currentPrimaryId(org));
  }

  @Override
  public OrganizationAccountResponse setPassword(
      UUID organizationId, UUID userId, SetAccountPasswordRequest request) {
    Organization org = requireOrganization(organizationId);
    User user = requireAccountOf(org, userId);

    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    // A password the operator handed over is usable immediately; a lead account parked in
    // PENDING_VERIFICATION would otherwise still be unable to sign in.
    if (user.getStatus() == User.UserStatus.PENDING_VERIFICATION) {
      user.setStatus(User.UserStatus.ACTIVE);
    }
    user = userRepository.save(user);

    log.info("Password reset for account {} of organization {}", user.getEmail(), organizationId);
    return toResponse(user, org, currentPrimaryId(org));
  }

  @Override
  public OrganizationAccountResponse setStatus(
      UUID organizationId, UUID userId, UpdateAccountStatusRequest request) {
    Organization org = requireOrganization(organizationId);
    User user = requireAccountOf(org, userId);

    user.setStatus(request.getStatus());
    user = userRepository.save(user);

    // Keep the real-estate agent record in step so a disabled account also stops acting as an
    // agent.
    realEstateAgentRepository
        .findByUserId(user.getId())
        .ifPresent(
            agent -> {
              agent.setStatus(
                  request.getStatus() == User.UserStatus.ACTIVE
                      ? RealEstateAgent.AgentStatus.ACTIVE
                      : RealEstateAgent.AgentStatus.INACTIVE);
              realEstateAgentRepository.save(agent);
            });

    return toResponse(user, org, currentPrimaryId(org));
  }

  @Override
  public OrganizationAccountResponse makePrimaryContact(UUID organizationId, UUID userId) {
    Organization org = requireOrganization(organizationId);
    User user = requireAccountOf(org, userId);

    if (user.getStatus() != User.UserStatus.ACTIVE) {
      throw new BusinessException("Only an active account can be the primary contact.");
    }

    org = promoteToPrimaryContact(org, user);
    return toResponse(user, org, currentPrimaryId(org));
  }

  @Override
  public void unlinkAccount(UUID organizationId, UUID userId) {
    Organization org = requireOrganization(organizationId);
    User user = requireAccountOf(org, userId);

    if (userId.equals(currentPrimaryId(org))) {
      throw new BusinessException(
          "This account is the company's primary contact. Promote another account first.");
    }

    realEstateAgentRepository.findByUserId(userId).ifPresent(realEstateAgentRepository::delete);
    user.setOrganization(null);
    userRepository.save(user);
    log.info("Unlinked account {} from organization {}", user.getEmail(), organizationId);
  }

  // --- helpers -------------------------------------------------------------

  private Organization requireOrganization(UUID organizationId) {
    return organizationRepository
        .findById(organizationId)
        .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
  }

  /** Loads a user and asserts it really belongs to this organization, so IDs cannot be crossed. */
  private User requireAccountOf(Organization org, UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    if (user.getOrganization() == null || !user.getOrganization().getId().equals(org.getId())) {
      throw new BusinessException("This account does not belong to the selected organization.");
    }
    return user;
  }

  /**
   * Makes {@code user} the organization's primary contact. For a real-estate company that also
   * means the super-agent flag, which only one agent may hold — so the incumbent is demoted first,
   * otherwise a second promotion would leave the company with two super agents.
   */
  private Organization promoteToPrimaryContact(Organization org, User user) {
    if (org.getType() == Organization.OrganizationType.REAL_ESTATE_COMPANY) {
      realEstateAgentRepository
          .findSuperAgentByOrganizationId(org.getId())
          .filter(agent -> !user.getId().equals(agent.getUserId()))
          .ifPresent(
              agent -> {
                agent.setIsSuperAgent(false);
                realEstateAgentRepository.save(agent);
              });
    }
    syncRealEstateAgent(org, user, true);
    org.setPrimaryContact(user);
    return organizationRepository.save(org);
  }

  private static UUID currentPrimaryId(Organization org) {
    return org.getPrimaryContact() != null ? org.getPrimaryContact().getId() : null;
  }

  private static String normalizePhone(String phone) {
    if (phone == null || phone.isBlank()) {
      return null;
    }
    return phone.trim();
  }

  /**
   * Real-estate staff need a {@link RealEstateAgent} row to appear in listings and own properties;
   * other organization types do not use agents at all.
   */
  private void syncRealEstateAgent(Organization org, User user, boolean superAgent) {
    if (org.getType() != Organization.OrganizationType.REAL_ESTATE_COMPANY) {
      return;
    }
    Optional<RealEstateAgent> existing = realEstateAgentRepository.findByUserId(user.getId());
    RealEstateAgent agent =
        existing.orElseGet(
            () ->
                RealEstateAgent.builder()
                    .user(user)
                    .organization(org)
                    .status(RealEstateAgent.AgentStatus.ACTIVE)
                    .isSuperAgent(false)
                    .build());
    agent.setOrganization(org);
    if (superAgent) {
      agent.setIsSuperAgent(true);
    }
    realEstateAgentRepository.save(agent);
  }

  private static OrganizationAccountResponse toResponse(
      User user, Organization org, UUID primaryContactId) {
    return OrganizationAccountResponse.builder()
        .id(user.getId())
        .email(user.getEmail())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .phoneNumber(user.getPhoneNumber())
        .status(user.getStatus())
        .roles(user.getRoles())
        .emailVerified(user.getEmailVerified())
        .primaryContact(user.getId().equals(primaryContactId))
        .organizationId(org.getId())
        .organizationName(org.getName())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .createdBy(user.getCreatedBy())
        .updatedBy(user.getUpdatedBy())
        .build();
  }
}
