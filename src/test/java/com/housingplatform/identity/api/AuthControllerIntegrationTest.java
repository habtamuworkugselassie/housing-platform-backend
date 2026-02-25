package com.housingplatform.identity.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.BaseIntegrationTest;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.LoginRequest;
import com.housingplatform.identity.dto.RegistrationRequest;
import com.housingplatform.identity.repository.UserRepository;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
  }

  @Test
  void testRegister_Success() throws Exception {
    RegistrationRequest request = new RegistrationRequest();
    request.setEmail("test@example.com");
    request.setPassword("Password123");
    request.setFirstName("Test");
    request.setLastName("User");
    request.setPhoneNumber("+251911111111");
    request.setRole(User.UserRole.BUYER);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").exists())
        .andExpect(jsonPath("$.refreshToken").exists())
        .andExpect(jsonPath("$.userId").exists())
        .andExpect(jsonPath("$.email").value("test@example.com"));
  }

  @Test
  void testRegister_InvalidEmail() throws Exception {
    RegistrationRequest request = new RegistrationRequest();
    request.setEmail("invalid-email");
    request.setPassword("Password123");
    request.setFirstName("Test");
    request.setLastName("User");
    request.setRole(User.UserRole.BUYER);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testRegister_WeakPassword() throws Exception {
    RegistrationRequest request = new RegistrationRequest();
    request.setEmail("test@example.com");
    request.setPassword("weak");
    request.setFirstName("Test");
    request.setLastName("User");
    request.setRole(User.UserRole.BUYER);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testLogin_Success() throws Exception {
    // Create a test user first
    Set<User.UserRole> roles = new HashSet<>();
    roles.add(User.UserRole.BUYER);

    User user =
        User.builder()
            .email("test@example.com")
            .passwordHash(passwordEncoder.encode("Password123"))
            .firstName("Test")
            .lastName("User")
            .status(User.UserStatus.ACTIVE)
            .emailVerified(true)
            .roles(roles)
            .build();
    userRepository.save(user);

    LoginRequest request = new LoginRequest();
    request.setUsername("test@example.com");
    request.setPassword("Password123");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists())
        .andExpect(jsonPath("$.refreshToken").exists())
        .andExpect(jsonPath("$.email").value("test@example.com"));
  }

  @Test
  void testLogin_InvalidCredentials() throws Exception {
    LoginRequest request = new LoginRequest();
    request.setUsername("nonexistent@example.com");
    request.setPassword("Password123");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Business Rule Violation"));
  }
}
