package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.AuthResponse;
import com.housingplatform.identity.dto.LoginRequest;
import com.housingplatform.identity.dto.RegistrationRequest;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.security.JwtTokenProvider;
import com.housingplatform.shared.security.PortalScope;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthenticationService {

  private final UserRepository userRepository;
  private final RealEstateAgentRepository realEstateAgentRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

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

    // Check if user account is valid (allow PENDING_VERIFICATION for newly registered users)
    if (user.getStatus() == User.UserStatus.SUSPENDED
        || user.getStatus() == User.UserStatus.INACTIVE) {
      throw new BusinessException("User account is not active");
    }

    // Extract scopes from user roles
    List<String> scopes =
        user.getRoles().stream()
            .map(role -> mapRoleToScope(role))
            .filter(scope -> scope != null)
            .collect(Collectors.toList());

    // Extract role names
    List<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toList());

    // Get organization_id if user is a real estate agent
    UUID organizationId = null;
    Optional<RealEstateAgent> agent = realEstateAgentRepository.findByUserId(user.getId());
    if (agent.isPresent()) {
      organizationId = agent.get().getOrganizationId();
    }

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

  private String mapRoleToScope(User.UserRole role) {
    return switch (role) {
      case BUYER -> PortalScope.BUYER;
      case BANKER -> PortalScope.BANKER;
      case REALTOR -> PortalScope.REALTOR;
      case SUPPLIER -> PortalScope.SUPPLIER;
      case ADMIN -> PortalScope.ADMIN;
    };
  }

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

      // Check if user account is valid (allow PENDING_VERIFICATION for newly registered users)
      if (user.getStatus() == User.UserStatus.SUSPENDED
          || user.getStatus() == User.UserStatus.INACTIVE) {
        throw new BusinessException("User account is not active");
      }

      // Extract scopes and roles
      List<String> scopes =
          user.getRoles().stream()
              .map(role -> mapRoleToScope(role))
              .filter(scope -> scope != null)
              .collect(Collectors.toList());

      List<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toList());

      // Get organization_id if user is a real estate agent
      UUID organizationId = null;
      Optional<RealEstateAgent> agent = realEstateAgentRepository.findByUserId(user.getId());
      if (agent.isPresent()) {
        organizationId = agent.get().getOrganizationId();
      }

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

    // Validate role - ADMIN cannot be self-assigned
    if (request.getRole() == User.UserRole.ADMIN) {
      throw new BusinessException(
          "Admin role cannot be self-assigned. Please contact system administrator.");
    }

    // Create new user
    Set<User.UserRole> roles = new HashSet<>();
    roles.add(request.getRole());

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
            .build();

    User savedUser = userRepository.save(user);

    // Extract scopes from user roles
    List<String> scopes =
        savedUser.getRoles().stream()
            .map(this::mapRoleToScope)
            .filter(scope -> scope != null)
            .collect(Collectors.toList());

    // Extract role names
    List<String> roleNames =
        savedUser.getRoles().stream().map(Enum::name).collect(Collectors.toList());

    // Get organization_id if user is a real estate agent (unlikely for new registrations, but check
    // anyway)
    UUID organizationId = null;
    Optional<RealEstateAgent> agent = realEstateAgentRepository.findByUserId(savedUser.getId());
    if (agent.isPresent()) {
      organizationId = agent.get().getOrganizationId();
    }

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

  public void logout() {
    // In a stateless JWT system, logout is primarily handled client-side
    // This method can be extended to:
    // 1. Blacklist tokens in Redis (if token blacklisting is implemented)
    // 2. Log logout events for audit purposes
    // 3. Invalidate refresh tokens
    // For now, it's a no-op as tokens are stateless
  }
}
