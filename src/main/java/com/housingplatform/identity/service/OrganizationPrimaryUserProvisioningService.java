package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.ProvisionOrganizationPrimaryUserRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.shared.exception.BusinessException;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the organization's {@link Organization#getPrimaryContact()} user when missing, using the
 * email on {@link com.housingplatform.identity.domain.OrganizationContact} (the same email the lead
 * submitted).
 */
@Service
@RequiredArgsConstructor
public class OrganizationPrimaryUserProvisioningService {

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final RealEstateAgentRepository realEstateAgentRepository;
  private final PasswordEncoder passwordEncoder;

  /**
   * @param emailFallback used only when org contact email is absent (e.g. legacy row); normally org
   *     contact carries the submitted email
   * @param phoneFallback optional phone when org contact has no phone lines
   */
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
            .roles(rolesForOrganizationType(org.getType()))
            .organization(org)
            .build();
    user = userRepository.save(user);

    org.setPrimaryContact(user);
    organizationRepository.save(org);

    if (org.getType() == Organization.OrganizationType.REAL_ESTATE_COMPANY) {
      if (!realEstateAgentRepository.existsByUserId(user.getId())) {
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
    return user;
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

  private static Set<User.UserRole> rolesForOrganizationType(Organization.OrganizationType type) {
    Set<User.UserRole> roles = new HashSet<>();
    switch (type) {
      case REAL_ESTATE_COMPANY:
        roles.add(User.UserRole.REALTOR);
        break;
      case BANK:
        roles.add(User.UserRole.BANKER);
        break;
      case SUPPLIER:
        roles.add(User.UserRole.SUPPLIER);
        break;
      default:
        roles.add(User.UserRole.SUPPLIER);
        break;
    }
    return roles;
  }
}
