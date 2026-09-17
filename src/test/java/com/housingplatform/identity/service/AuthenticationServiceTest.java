package com.housingplatform.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.AuthResponse;
import com.housingplatform.identity.dto.GoogleLoginRequest;
import com.housingplatform.identity.dto.LoginRequest;
import com.housingplatform.identity.dto.QuickRegistrationRequest;
import com.housingplatform.identity.dto.RegistrationRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.PasswordResetTokenRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.impl.AuthenticationServiceImpl;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.DuplicateResourceException;
import com.housingplatform.shared.security.JwtTokenProvider;
import com.housingplatform.shared.service.TokenBlacklistService;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;

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

  // ---------------------------------------------------------------- quick registration

  private QuickRegistrationRequest quickRequest() {
    QuickRegistrationRequest request = new QuickRegistrationRequest();
    request.setFullName("  Abebe   Kebede Alemu ");
    request.setPhoneNumber("0911223344");
    return request;
  }

  @Test
  void quickRegister_createsPasswordlessBuyerFromNameAndPhone() {
    when(userRepository.existsByPhoneNumber("+251911223344")).thenReturn(false);
    when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$random");
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              u.setId(UUID.randomUUID());
              return u;
            });
    when(jwtTokenProvider.generateToken(any(), any(), any(), any(), any())).thenReturn("access");
    when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh");

    AuthResponse response = authenticationService.quickRegister(quickRequest());

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    User user = saved.getValue();
    assertThat(user.getPhoneNumber()).isEqualTo("+251911223344");
    assertThat(user.getEmail()).isNull();
    assertThat(user.getFirstName()).isEqualTo("Abebe");
    assertThat(user.getLastName()).isEqualTo("Kebede Alemu");
    assertThat(user.getRoles()).containsExactly(User.UserRole.BUYER);
    assertThat(user.getStatus()).isEqualTo(User.UserStatus.PENDING_VERIFICATION);
    assertThat(user.getPhoneVerified()).isFalse();
    assertThat(response.getAccessToken()).isEqualTo("access");
    assertThat(response.getRoles()).containsExactly("BUYER");
    verify(verificationService).sendWhatsAppOtp("+251911223344");
  }

  @Test
  void quickRegister_storesOptionalEmailLowercasedAndUsesGivenPassword() {
    QuickRegistrationRequest request = quickRequest();
    request.setEmail(" Abebe@Example.com ");
    request.setPassword("Secret123");
    when(userRepository.existsByEmail("abebe@example.com")).thenReturn(false);
    when(passwordEncoder.encode("Secret123")).thenReturn("$2a$10$given");
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    authenticationService.quickRegister(request);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("abebe@example.com");
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$given");
  }

  @Test
  void quickRegister_rejectsKnownPhoneAndKnownEmailWith409() {
    when(userRepository.existsByPhoneNumber("+251911223344")).thenReturn(true);
    assertThatThrownBy(() -> authenticationService.quickRegister(quickRequest()))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessageContaining("WhatsApp code");

    when(userRepository.existsByPhoneNumber("+251911223344")).thenReturn(false);
    when(userRepository.existsByEmail("abebe@example.com")).thenReturn(true);
    QuickRegistrationRequest withEmail = quickRequest();
    withEmail.setEmail("abebe@example.com");
    assertThatThrownBy(() -> authenticationService.quickRegister(withEmail))
        .isInstanceOf(DuplicateResourceException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  void quickRegister_rejectsMalformedPhone() {
    QuickRegistrationRequest request = quickRequest();
    request.setPhoneNumber("12345");
    assertThatThrownBy(() -> authenticationService.quickRegister(request))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void quickRegister_stillSucceedsWhenTheVerificationCodeCannotBeSent() {
    when(passwordEncoder.encode(anyString())).thenReturn("hash");
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    doThrow(new BusinessException("Twilio down")).when(verificationService).sendWhatsAppOtp(any());

    AuthResponse response = authenticationService.quickRegister(quickRequest());
    assertThat(response).isNotNull();
  }

  @Test
  void splitFullName_handlesSingleAndMultipleNames() {
    assertThat(AuthenticationServiceImpl.splitFullName("Abebe")).containsExactly("Abebe", "");
    assertThat(AuthenticationServiceImpl.splitFullName(" Abebe  Kebede Alemu "))
        .containsExactly("Abebe", "Kebede Alemu");
  }

  // ---------------------------------------------------------------- google

  private GoogleLoginRequest googleRequest() {
    GoogleLoginRequest request = new GoogleLoginRequest();
    request.setIdToken("id-token");
    return request;
  }

  @Test
  void loginWithGoogle_opensAnActiveBuyerAccountForANewVerifiedEmail() {
    when(googleIdTokenVerifier.verify("id-token"))
        .thenReturn(
            new GoogleIdTokenVerifier.GoogleIdentity(
                "sub", "New.Buyer@Gmail.com", true, "New", "Buyer", "New Buyer", null));
    when(userRepository.findByEmail("new.buyer@gmail.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("hash");
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(jwtTokenProvider.generateToken(any(), any(), any(), any(), any())).thenReturn("access");

    AuthResponse response = authenticationService.loginWithGoogle(googleRequest());

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("new.buyer@gmail.com");
    assertThat(saved.getValue().getFirstName()).isEqualTo("New");
    assertThat(saved.getValue().getLastName()).isEqualTo("Buyer");
    assertThat(saved.getValue().getStatus()).isEqualTo(User.UserStatus.ACTIVE);
    assertThat(saved.getValue().getEmailVerified()).isTrue();
    assertThat(saved.getValue().getRoles()).containsExactly(User.UserRole.BUYER);
    assertThat(response.getAccessToken()).isEqualTo("access");
  }

  @Test
  void loginWithGoogle_signsIntoAnExistingAccountAndActivatesIt() {
    testUser.setStatus(User.UserStatus.PENDING_VERIFICATION);
    testUser.setEmailVerified(false);
    when(googleIdTokenVerifier.verify("id-token"))
        .thenReturn(
            new GoogleIdTokenVerifier.GoogleIdentity(
                "sub", "test@example.com", true, "Test", "User", "Test User", null));
    when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    authenticationService.loginWithGoogle(googleRequest());

    assertThat(testUser.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
    assertThat(testUser.getEmailVerified()).isTrue();
    verify(passwordEncoder, never()).encode(anyString());
  }

  @Test
  void loginWithGoogle_refusesUnverifiedEmailsAndDisabledAccounts() {
    when(googleIdTokenVerifier.verify("id-token"))
        .thenReturn(
            new GoogleIdTokenVerifier.GoogleIdentity(
                "sub", "x@y.com", false, null, null, null, null));
    assertThatThrownBy(() -> authenticationService.loginWithGoogle(googleRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("verified email");

    testUser.setStatus(User.UserStatus.SUSPENDED);
    when(googleIdTokenVerifier.verify("id-token"))
        .thenReturn(
            new GoogleIdTokenVerifier.GoogleIdentity(
                "sub", "test@example.com", true, null, null, null, null));
    when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
    assertThatThrownBy(() -> authenticationService.loginWithGoogle(googleRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("disabled");
  }
}
