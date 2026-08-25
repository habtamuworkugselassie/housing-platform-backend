package com.housingplatform.identity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationRoles;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.AuthResponse;
import com.housingplatform.identity.dto.LoginRequest;
import com.housingplatform.identity.dto.RegistrationRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.PasswordResetTokenRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.impl.AuthenticationServiceImpl;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.security.JwtTokenProvider;
import com.housingplatform.shared.security.PortalScope;
import com.housingplatform.shared.service.TokenBlacklistService;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Scope minting and role mapping for the super-admin tier. */
@ExtendWith(MockitoExtension.class)
class SuperAdminTierTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private RealEstateAgentRepository realEstateAgentRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock private PasswordResetEmailService passwordResetEmailService;
  @Mock private VerificationService verificationService;
  @Mock private TokenBlacklistService tokenBlacklistService;

  @InjectMocks private AuthenticationServiceImpl authenticationService;

  private User userWithRoles(User.UserRole... roles) {
    return User.builder()
        .id(UUID.randomUUID())
        .email("boss@example.com")
        .passwordHash("$2a$10$hash")
        .firstName("Super")
        .lastName("Admin")
        .status(User.UserStatus.ACTIVE)
        .emailVerified(true)
        .phoneVerified(true)
        .roles(EnumSet.copyOf(List.of(roles)))
        .build();
  }

  @SuppressWarnings("unchecked")
  private List<String> loginAndCaptureScopes(User user) {
    LoginRequest request = new LoginRequest();
    request.setUsername(user.getEmail());
    request.setPassword("password123");

    when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);
    when(jwtTokenProvider.generateToken(any(UUID.class), anyString(), any(), any(), any()))
        .thenReturn("access-token");
    when(jwtTokenProvider.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");

    AuthResponse response = authenticationService.login(request);
    assertNotNull(response);

    ArgumentCaptor<List<String>> scopes = ArgumentCaptor.forClass(List.class);
    verify(jwtTokenProvider)
        .generateToken(any(UUID.class), anyString(), scopes.capture(), any(), any());
    return scopes.getValue();
  }

  @Test
  void superAdminTokenCarriesBothAdminAndSuperAdminScopes() {
    List<String> scopes = loginAndCaptureScopes(userWithRoles(User.UserRole.SUPER_ADMIN));

    // admin too, so every existing ADMIN_SECURED endpoint stays reachable for a super admin.
    assertTrue(scopes.contains(PortalScope.SUPER_ADMIN), "expected super_admin in " + scopes);
    assertTrue(scopes.contains(PortalScope.ADMIN), "expected admin in " + scopes);
  }

  @Test
  void plainAdminTokenNeverCarriesSuperAdminScope() {
    List<String> scopes = loginAndCaptureScopes(userWithRoles(User.UserRole.ADMIN));

    assertTrue(scopes.contains(PortalScope.ADMIN));
    assertFalse(scopes.contains(PortalScope.SUPER_ADMIN), "plain admin must not be a super admin");
  }

  @Test
  void overlappingAdminAndSuperAdminRolesDoNotDuplicateScopes() {
    List<String> scopes =
        loginAndCaptureScopes(userWithRoles(User.UserRole.ADMIN, User.UserRole.SUPER_ADMIN));

    assertEquals(1, scopes.stream().filter(PortalScope.ADMIN::equals).count(), "scopes: " + scopes);
    assertTrue(scopes.contains(PortalScope.SUPER_ADMIN));
  }

  @Test
  void superAdminRoleCannotBeSelfAssignedAtRegistration() {
    RegistrationRequest request = new RegistrationRequest();
    request.setEmail("attacker@example.com");
    request.setPassword("Password1");
    request.setFirstName("A");
    request.setLastName("B");
    request.setRole(User.UserRole.SUPER_ADMIN);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> authenticationService.register(request));
    assertTrue(ex.getMessage().contains("cannot be self-assigned"));
    verify(userRepository, never()).save(any());
  }

  @Test
  void onlyAdminAndSuperAdminAreConsideredPrivileged() {
    assertTrue(User.UserRole.ADMIN.isPrivileged());
    assertTrue(User.UserRole.SUPER_ADMIN.isPrivileged());
    assertFalse(User.UserRole.BUYER.isPrivileged());
    assertFalse(User.UserRole.BANKER.isPrivileged());
    assertFalse(User.UserRole.REALTOR.isPrivileged());
    assertFalse(User.UserRole.SUPPLIER.isPrivileged());
  }

  /** Every sponsor company type must be able to receive a login, not only the big three. */
  @ParameterizedTest
  @EnumSource(Organization.OrganizationType.class)
  void everyOrganizationTypeMapsToAPortalRole(Organization.OrganizationType type) {
    User.UserRole role = OrganizationRoles.portalRoleFor(type);

    assertNotNull(role);
    assertFalse(role.isPrivileged(), "company staff must not get a privileged role");
    assertTrue(OrganizationRoles.matchesPortalRole(type, OrganizationRoles.defaultRolesFor(type)));
  }

  @Test
  void portalRolesFollowOrganizationType() {
    assertEquals(
        User.UserRole.REALTOR,
        OrganizationRoles.portalRoleFor(Organization.OrganizationType.REAL_ESTATE_COMPANY));
    assertEquals(
        User.UserRole.BANKER, OrganizationRoles.portalRoleFor(Organization.OrganizationType.BANK));
    // Sponsor types without a portal of their own share the supplier portal.
    assertEquals(
        User.UserRole.SUPPLIER,
        OrganizationRoles.portalRoleFor(Organization.OrganizationType.MEDIA_COMPANY));
    assertEquals(
        User.UserRole.SUPPLIER,
        OrganizationRoles.portalRoleFor(Organization.OrganizationType.CONTRACTOR));
  }

  @Test
  void aBankStaffRoleDoesNotSatisfyARealEstateCompany() {
    assertFalse(
        OrganizationRoles.matchesPortalRole(
            Organization.OrganizationType.REAL_ESTATE_COMPANY, EnumSet.of(User.UserRole.BANKER)));
  }
}
