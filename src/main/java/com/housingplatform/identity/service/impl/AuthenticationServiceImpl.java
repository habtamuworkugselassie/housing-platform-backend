package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.PasswordResetToken;
import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.AuthResponse;
import com.housingplatform.identity.dto.ForgotPasswordRequest;
import com.housingplatform.identity.dto.LoginRequest;
import com.housingplatform.identity.dto.RegistrationRequest;
import com.housingplatform.identity.dto.ResetPasswordRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.PasswordResetTokenRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.AuthenticationService;
import com.housingplatform.identity.service.PasswordResetEmailService;
import com.housingplatform.identity.service.VerificationService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.JwtTokenProvider;
import com.housingplatform.shared.security.PortalScope;
import com.housingplatform.shared.service.TokenBlacklistService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthenticationServiceImpl implements AuthenticationService {

  private static final int RESET_TOKEN_EXPIRY_HOURS = 1;
  private static final int RESET_TOKEN_BYTES = 32;

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final RealEstateAgentRepository realEstateAgentRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordResetEmailService passwordResetEmailService;
  private final VerificationService verificationService;
  private final TokenBlacklistService tokenBlacklistService;

  @Value("${app.password-reset.expiry-hours:" + RESET_TOKEN_EXPIRY_HOURS + "}")
  private int resetTokenExpiryHours;

  @Override
  public AuthResponse login(LoginRequest request) {
    // Find user by email, username, or phone number
    User user =
        userRepository
            .findByEmail(request.getUsername())
            .orElseGet(
                () ->
                    userRepository
                        .findByPhoneNumber(request.getUsername())
                        .orElseThrow(() -> new BusinessException("Invalid credentials")));

    // Verify password
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new BusinessException("Invalid credentials");
    }

    // Check if user account is valid (allow PENDING_VERIFICATION for newly
    // registered users)
    if (user.getStatus() == User.UserStatus.SUSPENDED
        || user.getStatus() == User.UserStatus.INACTIVE) {
      throw new BusinessException("User account is disabled");
    }

    // Extract scopes from user roles
    List<String> scopes = mapRolesToScopes(user.getRoles());

    // Extract role names
    List<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toList());

    UUID organizationId = resolveOrganizationIdForToken(user);

    // Generate tokens
    String accessToken =
        jwtTokenProvider.generateToken(
            user.getId(), user.getEmail(), scopes, roles, organizationId);

    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

    return AuthResponse.builder()
        .accessToken(accessToken)
        .tokenType("Bearer")
        .expiresIn(3600L) // 1 hour
        .refreshToken(refreshToken)
        .userId(user.getId())
        .email(user.getEmail())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .scopes(scopes)
        .roles(roles)
        .build();
  }

  /**
   * Token scopes for a role set. {@link User.UserRole#SUPER_ADMIN} carries {@code admin} as well so
   * every existing {@code ADMIN_SECURED} endpoint stays reachable, plus {@code super_admin} for the
   * endpoints a plain admin must not touch.
   */
  private List<String> mapRolesToScopes(Collection<User.UserRole> roles) {
    return roles.stream()
        .flatMap(role -> mapRoleToScopes(role).stream())
        .distinct()
        .collect(Collectors.toList());
  }

  private List<String> mapRoleToScopes(User.UserRole role) {
    return switch (role) {
      case BUYER -> List.of(PortalScope.BUYER);
      case BANKER -> List.of(PortalScope.BANKER);
      case REALTOR -> List.of(PortalScope.REALTOR);
      case SUPPLIER -> List.of(PortalScope.SUPPLIER);
      case ADMIN -> List.of(PortalScope.ADMIN);
      case SUPER_ADMIN -> List.of(PortalScope.ADMIN, PortalScope.SUPER_ADMIN);
    };
  }

  /**
   * JWT {@code organization_id} claim: prefer real-estate agent linkage, else direct {@link
   * User#getOrganization()} (e.g. banker/supplier joined via registration).
   */
  private UUID resolveOrganizationIdForToken(User user) {
    Optional<RealEstateAgent> agent = realEstateAgentRepository.findByUserId(user.getId());
    if (agent.isPresent()) {
      return agent.get().getOrganizationId();
    }
    if (user.getOrganization() != null) {
      return user.getOrganization().getId();
    }
    return null;
  }

  private Organization resolveOrganizationForSelfRegistration(
      User.UserRole role, UUID organizationId) {
    Organization org =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
    if (org.getStatus() != Organization.OrganizationStatus.APPROVED) {
      throw new BusinessException(
          "Only verified (approved) organizations can be selected during registration");
    }
    switch (role) {
      case REALTOR:
        if (org.getType() != Organization.OrganizationType.REAL_ESTATE_COMPANY) {
          throw new BusinessException("Selected organization must be a real estate company");
        }
        break;
      case BANKER:
        if (org.getType() != Organization.OrganizationType.BANK) {
          throw new BusinessException("Selected organization must be a bank");
        }
        break;
      case SUPPLIER:
        if (org.getType() != Organization.OrganizationType.SUPPLIER) {
          throw new BusinessException("Selected organization must be a supplier");
        }
        break;
      default:
        throw new BusinessException(
            "Organization can only be linked when registering as realtor, banker, or supplier");
    }
    return org;
  }

  @Override
  public AuthResponse refreshToken(String refreshToken) {
    if (refreshToken == null || refreshToken.trim().isEmpty()) {
      throw new BusinessException("Refresh token is required");
    }

    try {
      var claims = jwtTokenProvider.parseClaims(refreshToken);

      // Check if token has the refresh type claim
      Object typeClaim = claims.get("type");
      if (typeClaim == null || !"refresh".equals(typeClaim.toString())) {
        throw new BusinessException("Invalid refresh token: token is not a refresh token");
      }

      // Check if token is expired
      if (claims.getExpiration() != null && claims.getExpiration().before(new java.util.Date())) {
        throw new BusinessException("Refresh token has expired. Please login again.");
      }

      String subject = claims.getSubject();
      if (subject == null || subject.trim().isEmpty()) {
        throw new BusinessException("Invalid refresh token: missing user ID");
      }

      UUID userId = UUID.fromString(subject);
      User user =
          userRepository
              .findById(userId)
              .orElseThrow(() -> new BusinessException("User not found"));

      // Check if user account is valid (allow PENDING_VERIFICATION for newly
      // registered users)
      if (user.getStatus() == User.UserStatus.SUSPENDED
          || user.getStatus() == User.UserStatus.INACTIVE) {
        throw new BusinessException("User account is disabled");
      }

      // Extract scopes and roles
      List<String> scopes = mapRolesToScopes(user.getRoles());

      List<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toList());

      UUID organizationId = resolveOrganizationIdForToken(user);

      // Generate new tokens
      String newAccessToken =
          jwtTokenProvider.generateToken(
              user.getId(), user.getEmail(), scopes, roles, organizationId);

      String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

      return AuthResponse.builder()
          .accessToken(newAccessToken)
          .tokenType("Bearer")
          .expiresIn(3600L)
          .refreshToken(newRefreshToken)
          .userId(user.getId())
          .email(user.getEmail())
          .firstName(user.getFirstName())
          .lastName(user.getLastName())
          .scopes(scopes)
          .roles(roles)
          .build();
    } catch (BusinessException e) {
      // Re-throw business exceptions as-is
      throw e;
    } catch (IllegalArgumentException e) {
      // UUID parsing error
      throw new BusinessException("Invalid refresh token: malformed user ID");
    } catch (io.jsonwebtoken.ExpiredJwtException e) {
      throw new BusinessException("Refresh token has expired. Please login again.");
    } catch (io.jsonwebtoken.JwtException e) {
      throw new BusinessException(
          "Invalid refresh token: "
              + (e.getMessage() != null ? e.getMessage() : "Token validation failed"));
    } catch (Exception e) {
      throw new BusinessException(
          "Invalid refresh token: "
              + (e.getMessage() != null ? e.getMessage() : "Token validation failed"));
    }
  }

  @Override
  public AuthResponse register(RegistrationRequest request) {
    // Check if email already exists
    if (userRepository.findByEmail(request.getEmail()).isPresent()) {
      throw new BusinessException("Email already registered");
    }

    // Check if phone number already exists (if provided)
    if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
      if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
        throw new BusinessException("Phone number already registered");
      }
    }

    // Validate role - ADMIN / SUPER_ADMIN cannot be self-assigned
    if (request.getRole() != null && request.getRole().isPrivileged()) {
      throw new BusinessException(
          "Admin role cannot be self-assigned. Please contact system administrator.");
    }

    // Create new user
    Set<User.UserRole> roles = new HashSet<>();
    roles.add(request.getRole());

    Organization linkedOrganization = null;
    if (request.getOrganizationId() != null) {
      linkedOrganization =
          resolveOrganizationForSelfRegistration(request.getRole(), request.getOrganizationId());
    }

    User user =
        User.builder()
            .email(request.getEmail().toLowerCase().trim())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
            .status(
                User.UserStatus
                    .PENDING_VERIFICATION) // Require email verification before activation
            .emailVerified(false)
            .phoneVerified(false)
            .roles(roles)
            .organization(linkedOrganization)
            .build();

    User savedUser = userRepository.save(user);

    if (request.getRole() == User.UserRole.REALTOR
        && savedUser.getOrganization() != null
        && savedUser.getOrganization().getType()
            == Organization.OrganizationType.REAL_ESTATE_COMPANY) {
      if (!realEstateAgentRepository.existsByUserId(savedUser.getId())) {
        realEstateAgentRepository.save(
            RealEstateAgent.builder()
                .user(savedUser)
                .organization(savedUser.getOrganization())
                .status(RealEstateAgent.AgentStatus.ACTIVE)
                .isSuperAgent(false)
                .build());
      }
    }

    // Extract scopes from user roles
    List<String> scopes = mapRolesToScopes(savedUser.getRoles());

    // Extract role names
    List<String> roleNames =
        savedUser.getRoles().stream().map(Enum::name).collect(Collectors.toList());

    UUID organizationId = resolveOrganizationIdForToken(savedUser);

    // Generate tokens
    String accessToken =
        jwtTokenProvider.generateToken(
            savedUser.getId(), savedUser.getEmail(), scopes, roleNames, organizationId);

    String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser.getId());

    return AuthResponse.builder()
        .accessToken(accessToken)
        .tokenType("Bearer")
        .expiresIn(3600L) // 1 hour
        .refreshToken(refreshToken)
        .userId(savedUser.getId())
        .email(savedUser.getEmail())
        .firstName(savedUser.getFirstName())
        .lastName(savedUser.getLastName())
        .scopes(scopes)
        .roles(roleNames)
        .build();
  }

  @Override
  public void logout(String accessToken) {
    if (accessToken != null && !accessToken.isBlank()) {
      try {
        var claims = jwtTokenProvider.parseClaims(accessToken);
        tokenBlacklistService.blacklist(accessToken, claims.getExpiration());
      } catch (Exception e) {
        // Token may already be expired or malformed; blacklisting is best-effort.
        // The client-side state will still be cleared.
      }
    }
  }

  @Override
  public void requestPasswordReset(ForgotPasswordRequest request) {
    Optional<User> userOpt = userRepository.findByEmail(request.getEmail().trim().toLowerCase());
    if (userOpt.isEmpty()) {
      return;
    }
    User user = userOpt.get();
    passwordResetTokenRepository.deleteByUserId(user.getId());

    String token = generateSecureToken();
    Instant expiresAt = Instant.now().plusSeconds(resetTokenExpiryHours * 3600L);
    PasswordResetToken resetToken =
        PasswordResetToken.builder().token(token).userId(user.getId()).expiresAt(expiresAt).build();
    passwordResetTokenRepository.save(resetToken);

    passwordResetEmailService.sendPasswordResetEmail(user.getEmail(), token);
  }

  @Override
  public void resetPassword(ResetPasswordRequest request) {
    Instant now = Instant.now();
    PasswordResetToken resetToken =
        passwordResetTokenRepository
            .findByTokenAndUsedAtIsNullAndExpiresAtAfter(request.getToken().trim(), now)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "Invalid or expired reset link. Please request a new password reset."));

    User user =
        userRepository
            .findById(resetToken.getUserId())
            .orElseThrow(() -> new BusinessException("User not found"));

    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);

    resetToken.setUsedAt(now);
    passwordResetTokenRepository.save(resetToken);
  }

  private static String generateSecureToken() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[RESET_TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  @Override
  public void markPhoneAsVerified(String phoneNumber) {
    if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
      return;
    }
    User user =
        userRepository
            .findByPhoneNumber(phoneNumber)
            .orElseThrow(
                () -> new BusinessException("User not found for phone number: " + phoneNumber));

    user.setPhoneVerified(true);
    // If we consider WhatsApp sufficient for activation, we can update status:
    if (user.getStatus() == User.UserStatus.PENDING_VERIFICATION) {
      user.setStatus(User.UserStatus.ACTIVE);
    }
    userRepository.save(user);
  }

  @Override
  public void requestOtpLogin(String phoneNumber) {
    verificationService.sendWhatsAppOtp(phoneNumber);
  }

  @Override
  public AuthResponse confirmOtpLogin(String phoneNumber, String code) {
    boolean isValid = verificationService.verifyOtp(phoneNumber, code);
    if (!isValid) {
      throw new BusinessException("Invalid or expired verification code");
    }

    User user =
        userRepository
            .findByPhoneNumber(phoneNumber)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "User not found for this phone number. Please register."));

    if (user.getStatus() == User.UserStatus.SUSPENDED
        || user.getStatus() == User.UserStatus.INACTIVE) {
      throw new BusinessException("User account is disabled");
    }

    if (!user.getPhoneVerified()) {
      user.setPhoneVerified(true);
      if (user.getStatus() == User.UserStatus.PENDING_VERIFICATION) {
        user.setStatus(User.UserStatus.ACTIVE);
      }
      user = userRepository.save(user);
    }

    List<String> scopes = mapRolesToScopes(user.getRoles());

    List<String> roleNames = user.getRoles().stream().map(Enum::name).collect(Collectors.toList());

    UUID organizationId = null;
    Optional<RealEstateAgent> agent = realEstateAgentRepository.findByUserId(user.getId());
    if (agent.isPresent()) {
      organizationId = agent.get().getOrganizationId();
    }

    String accessToken =
        jwtTokenProvider.generateToken(
            user.getId(), user.getEmail(), scopes, roleNames, organizationId);
    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

    return AuthResponse.builder()
        .accessToken(accessToken)
        .tokenType("Bearer")
        .expiresIn(3600L) // 1 hour
        .refreshToken(refreshToken)
        .userId(user.getId())
        .email(user.getEmail())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .scopes(scopes)
        .roles(roleNames)
        .build();
  }
}
