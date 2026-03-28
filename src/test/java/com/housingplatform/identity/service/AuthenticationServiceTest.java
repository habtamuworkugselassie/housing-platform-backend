package com.housingplatform.identity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.AuthResponse;
import com.housingplatform.identity.dto.LoginRequest;
import com.housingplatform.identity.dto.RegistrationRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.PasswordResetTokenRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.impl.AuthenticationServiceImpl;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.security.JwtTokenProvider;
import com.housingplatform.shared.service.TokenBlacklistService;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private JwtTokenProvider jwtTokenProvider;

  @Mock
  private com.housingplatform.identity.repository.RealEstateAgentRepository
      realEstateAgentRepository;

  @Mock private OrganizationRepository organizationRepository;

  @Mock private PasswordResetTokenRepository passwordResetTokenRepository;

  @Mock private PasswordResetEmailService passwordResetEmailService;

  @Mock private VerificationService verificationService;

  @Mock private TokenBlacklistService tokenBlacklistService;

  @InjectMocks private AuthenticationServiceImpl authenticationService;

  private User testUser;
  private UUID testUserId;

  @BeforeEach
  void setUp() {
    testUserId = UUID.randomUUID();
    Set<User.UserRole> roles = new HashSet<>();
    roles.add(User.UserRole.BUYER);

    testUser =
        User.builder()
            .id(testUserId)
            .email("test@example.com")
            .passwordHash("$2a$10$encodedPassword")
            .firstName("Test")
            .lastName("User")
            .phoneNumber("+251911111111")
            .status(User.UserStatus.ACTIVE)
            .emailVerified(true)
            .phoneVerified(false)
            .roles(roles)
            .build();
  }

  @Test
  void testLogin_Success() {
    // Arrange
    LoginRequest request = new LoginRequest();
    request.setUsername("test@example.com");
    request.setPassword("password123");

    when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
    when(jwtTokenProvider.generateToken(any(UUID.class), anyString(), any(), any(), any()))
        .thenReturn("access-token");
    when(jwtTokenProvider.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");
    when(realEstateAgentRepository.findByUserId(testUserId)).thenReturn(Optional.empty());

    // Act
    AuthResponse response = authenticationService.login(request);

    // Assert
    assertNotNull(response);
    assertEquals("access-token", response.getAccessToken());
    assertEquals("refresh-token", response.getRefreshToken());
    assertEquals("Bearer", response.getTokenType());
    assertEquals(testUserId, response.getUserId());
    assertEquals("test@example.com", response.getEmail());
    verify(userRepository).findByEmail("test@example.com");
    verify(passwordEncoder).matches("password123", testUser.getPasswordHash());
    verify(jwtTokenProvider).generateToken(any(UUID.class), anyString(), any(), any(), any());
    verify(jwtTokenProvider).generateRefreshToken(testUserId);
  }

  @Test
  void testLogin_WithPhoneNumber() {
    // Arrange
    LoginRequest request = new LoginRequest();
    request.setUsername("+251911111111");
    request.setPassword("password123");

    when(userRepository.findByEmail("+251911111111")).thenReturn(Optional.empty());
    when(userRepository.findByPhoneNumber("+251911111111")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
    when(jwtTokenProvider.generateToken(any(UUID.class), anyString(), any(), any(), any()))
        .thenReturn("access-token");
    when(jwtTokenProvider.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");
    when(realEstateAgentRepository.findByUserId(testUserId)).thenReturn(Optional.empty());

    // Act
    AuthResponse response = authenticationService.login(request);

    // Assert
    assertNotNull(response);
    verify(userRepository).findByEmail("+251911111111");
    verify(userRepository).findByPhoneNumber("+251911111111");
  }

  @Test
  void testLogin_InvalidCredentials_UserNotFound() {
    // Arrange
    LoginRequest request = new LoginRequest();
    request.setUsername("nonexistent@example.com");
    request.setPassword("password123");

    when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());
    when(userRepository.findByPhoneNumber("nonexistent@example.com")).thenReturn(Optional.empty());

    // Act & Assert
    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> {
              authenticationService.login(request);
            });

    assertEquals("Invalid credentials", exception.getMessage());
    verify(userRepository).findByEmail("nonexistent@example.com");
    verify(userRepository).findByPhoneNumber("nonexistent@example.com");
  }

  @Test
  void testLogin_InvalidCredentials_WrongPassword() {
    // Arrange
    LoginRequest request = new LoginRequest();
    request.setUsername("test@example.com");
    request.setPassword("wrongpassword");

    when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("wrongpassword", testUser.getPasswordHash())).thenReturn(false);

    // Act & Assert
    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> {
              authenticationService.login(request);
            });

    assertEquals("Invalid credentials", exception.getMessage());
    verify(passwordEncoder).matches("wrongpassword", testUser.getPasswordHash());
  }

  @Test
  void testLogin_UserSuspended() {
    // Arrange
    testUser.setStatus(User.UserStatus.SUSPENDED);
    LoginRequest request = new LoginRequest();
    request.setUsername("test@example.com");
    request.setPassword("password123");

    when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
    when(realEstateAgentRepository.findByUserId(testUserId)).thenReturn(Optional.empty());

    // Act & Assert
    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> {
              authenticationService.login(request);
            });

    assertEquals("User account is disabled", exception.getMessage());
  }

  @Test
  void testRegister_Success() {
    // Arrange
    RegistrationRequest request = new RegistrationRequest();
    request.setEmail("newuser@example.com");
    request.setPassword("Password123");
    request.setFirstName("New");
    request.setLastName("User");
    request.setPhoneNumber("+251922222222");
    request.setRole(User.UserRole.BUYER);

    when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
    when(userRepository.findByPhoneNumber("+251922222222")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("Password123")).thenReturn("$2a$10$encodedPassword");
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User user = invocation.getArgument(0);
              user.setId(UUID.randomUUID());
              return user;
            });
    when(jwtTokenProvider.generateToken(any(UUID.class), anyString(), any(), any(), any()))
        .thenReturn("access-token");
    when(jwtTokenProvider.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");
    when(realEstateAgentRepository.existsByUserId(any(UUID.class))).thenReturn(false);

    // Act
    AuthResponse response = authenticationService.register(request);

    // Assert
    assertNotNull(response);
    assertEquals("access-token", response.getAccessToken());
    assertEquals("refresh-token", response.getRefreshToken());
    verify(userRepository).findByEmail("newuser@example.com");
    verify(userRepository).findByPhoneNumber("+251922222222");
    verify(userRepository).save(any(User.class));
    verify(passwordEncoder).encode("Password123");
  }

  @Test
  void testRegister_EmailAlreadyExists() {
    // Arrange
    RegistrationRequest request = new RegistrationRequest();
    request.setEmail("existing@example.com");
    request.setPassword("Password123");
    request.setFirstName("New");
    request.setLastName("User");
    request.setRole(User.UserRole.BUYER);

    when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(testUser));

    // Act & Assert
    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> {
              authenticationService.register(request);
            });

    assertEquals("Email already registered", exception.getMessage());
    verify(userRepository).findByEmail("existing@example.com");
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void testRegister_AdminRoleNotAllowed() {
    // Arrange
    RegistrationRequest request = new RegistrationRequest();
    request.setEmail("admin@example.com");
    request.setPassword("Password123");
    request.setFirstName("Admin");
    request.setLastName("User");
    request.setRole(User.UserRole.ADMIN);

    when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());

    // Act & Assert
    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> {
              authenticationService.register(request);
            });

    assertEquals(
        "Admin role cannot be self-assigned. Please contact system administrator.",
        exception.getMessage());
    verify(userRepository, never()).save(any(User.class));
  }
}
