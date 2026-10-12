package com.housingplatform.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.UserResponse;
import com.housingplatform.identity.dto.UserUpdateRequest;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.UserMapper;
import com.housingplatform.media.repository.MediaAttachmentRepository;
import com.housingplatform.media.service.MediaStorageService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** PUT /users/me runs through updateUser; a non-admin must not be able to touch admin fields. */
@ExtendWith(MockitoExtension.class)
class UserSelfUpdateTest {

  @Mock private UserRepository userRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private RealEstateAgentRepository realEstateAgentRepository;
  @Mock private MediaAttachmentRepository mediaAttachmentRepository;
  @Mock private MediaStorageService mediaStorageService;
  @Mock private PasswordEncoder passwordEncoder;
  @Spy private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

  @InjectMocks private UserServiceImpl service;

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void aBuyerCanChangeTheirNameButNotTheirRolesStatusOrEmail() {
    UUID id = UUID.randomUUID();
    User buyer =
        User.builder()
            .id(id)
            .email("buyer@example.com")
            .passwordHash("x")
            .firstName("Old")
            .lastName("Name")
            .status(User.UserStatus.SUSPENDED)
            .roles(new HashSet<>(Set.of(User.UserRole.BUYER)))
            .build();
    signInAs(id, "buyer");
    when(userRepository.findById(id)).thenReturn(Optional.of(buyer));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(mediaAttachmentRepository.findByUserIdOrderByDisplayOrderAsc(id)).thenReturn(List.of());

    UserUpdateRequest request = new UserUpdateRequest();
    request.setFirstName("New");
    request.setRoles(Set.of(User.UserRole.SUPER_ADMIN));
    request.setStatus(User.UserStatus.ACTIVE);
    request.setEnabled(true);
    request.setEmail("someone-else@example.com");

    UserResponse response = service.updateUser(id, request);

    assertThat(buyer.getFirstName()).isEqualTo("New");
    assertThat(buyer.getRoles()).containsExactly(User.UserRole.BUYER);
    assertThat(buyer.getStatus()).isEqualTo(User.UserStatus.SUSPENDED);
    assertThat(buyer.getEmail()).isEqualTo("buyer@example.com");
    assertThat(response.getRoles()).containsExactly(User.UserRole.BUYER);
  }

  private static void signInAs(UUID id, String scope) {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject(id.toString())
            .claim("scope", scope)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }
}
