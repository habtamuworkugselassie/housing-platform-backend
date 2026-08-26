package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationRoles;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.ProvisionOrganizationPrimaryUserRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.OrganizationPrimaryUserProvisioningService;
import com.housingplatform.shared.exception.BusinessException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationPrimaryUserProvisioningServiceImpl
    implements OrganizationPrimaryUserProvisioningService {

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final RealEstateAgentRepository realEstateAgentRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public User provisionPrimaryContactIfMissing(
      Organization org,
      ProvisionOrganizationPrimaryUserRequest request,
      String emailFallback,
      String phoneFallback) {
    if (org.getPrimaryContact() != null) {
      return org.getPrimaryContact();
    }
    String email = resolveRegistrantEmail(org, emailFallback);
    if (email == null || email.isBlank()) {
      throw new BusinessException("No organization contact email on file for this registration");
    }
    String normalized = email.trim().toLowerCase();
    if (userRepository.findByEmail(normalized).isPresent()) {
      throw new BusinessException(
          "A user with this email already exists. Set them as the organization's primary contact, then try again.");
    }

    User user =
        User.builder()
            .email(normalized)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName().trim())
            .lastName(request.getLastName().trim())
            .phoneNumber(resolveRegistrantPhone(org, phoneFallback))
            .status(User.UserStatus.ACTIVE)
            .emailVerified(false)
            .phoneVerified(false)
            .roles(OrganizationRoles.defaultRolesFor(org.getType()))
            .organization(org)
            .build();
    user = userRepository.save(user);

    org.setPrimaryContact(user);
    organizationRepository.save(org);

    ensureRealEstateSuperAgent(org, user);
    return user;
  }

  @Override
  @Transactional
  public User linkExhibitionLeadUser(Organization org, String emailFallback, String phoneFallback) {
    if (org.getPrimaryContact() != null) {
      return org.getPrimaryContact();
    }
    String email = resolveRegistrantEmail(org, emailFallback);
    if (email == null || email.isBlank()) {
      throw new BusinessException("No organization contact email on file for this registration");
    }
    String normalized = email.trim().toLowerCase();
    Optional<User> existingOpt = userRepository.findByEmail(normalized);
    if (existingOpt.isPresent()) {
      User u = existingOpt.get();
      if (u.getOrganization() != null && !u.getOrganization().getId().equals(org.getId())) {
        throw new BusinessException(
            "A user with this email already belongs to another organization.");
      }
      u.setOrganization(org);
      u = userRepository.save(u);
      org.setPrimaryContact(u);
      organizationRepository.save(org);
      ensureRealEstateSuperAgent(org, u);
      return u;
    }

    String[] names = derivePlaceholderNamesFromEmail(normalized);
    String phone = resolveRegistrantPhone(org, phoneFallback);
    User user =
        User.builder()
            .email(normalized)
            .passwordHash(passwordEncoder.encode(randomUnusablePassword()))
            .firstName(names[0])
            .lastName(names[1])
            .phoneNumber(phone)
            .status(User.UserStatus.PENDING_VERIFICATION)
            .emailVerified(false)
            .phoneVerified(false)
            .roles(OrganizationRoles.defaultRolesFor(org.getType()))
            .organization(org)
            .build();
    user = userRepository.save(user);
    org.setPrimaryContact(user);
    organizationRepository.save(org);
    ensureRealEstateSuperAgent(org, user);
    return user;
  }

  @Override
  @Transactional
  public void completePendingPrimaryContact(
      User user, ProvisionOrganizationPrimaryUserRequest request) {
    if (user.getStatus() != User.UserStatus.PENDING_VERIFICATION) {
      return;
    }
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setFirstName(request.getFirstName().trim());
    user.setLastName(request.getLastName().trim());
    user.setStatus(User.UserStatus.ACTIVE);
    userRepository.save(user);
  }

  private void ensureRealEstateSuperAgent(Organization org, User user) {
    if (org.getType() == Organization.OrganizationType.REAL_ESTATE_COMPANY
        && !realEstateAgentRepository.existsByUserId(user.getId())) {
      RealEstateAgent agent =
          RealEstateAgent.builder()
              .user(user)
              .organization(org)
              .status(RealEstateAgent.AgentStatus.ACTIVE)
              .isSuperAgent(true)
              .build();
      realEstateAgentRepository.save(agent);
    }
  }

  private static String randomUnusablePassword() {
    byte[] buf = new byte[32];
    new SecureRandom().nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  private static String[] derivePlaceholderNamesFromEmail(String normalizedEmail) {
    int at = normalizedEmail.indexOf('@');
    String local = at > 0 ? normalizedEmail.substring(0, at) : "user";
    local = local.replaceAll("[^a-zA-Z0-9._-]", "");
    if (local.isEmpty()) {
      local = "user";
    }
    String[] parts = local.split("[._-]", 2);
    String first = capitalizeWord(parts[0]);
    String last = parts.length > 1 && !parts[1].isEmpty() ? capitalizeWord(parts[1]) : "Exhibition";
    return new String[] {first, last};
  }

  private static String capitalizeWord(String s) {
    if (s == null || s.isEmpty()) {
      return "User";
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
  }

  private static String resolveRegistrantEmail(Organization org, String emailFallback) {
    if (org.getContact() != null
        && org.getContact().getEmail() != null
        && !org.getContact().getEmail().isBlank()) {
      return org.getContact().getEmail().trim();
    }
    return emailFallback != null ? emailFallback.trim() : null;
  }

  private static String resolveRegistrantPhone(Organization org, String phoneFallback) {
    if (org.getContact() != null && org.getContact().getPhones() != null) {
      for (var p : org.getContact().getPhones()) {
        if (p.getNumber() != null && !p.getNumber().isBlank()) {
          return p.getNumber().trim();
        }
      }
    }
    return phoneFallback != null ? phoneFallback.trim() : null;
  }
}
