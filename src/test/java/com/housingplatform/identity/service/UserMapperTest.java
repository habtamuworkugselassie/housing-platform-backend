package com.housingplatform.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.UserResponse;
import com.housingplatform.identity.dto.UserUpdateRequest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class UserMapperTest {

  private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

  @Test
  void toResponse_shouldMapUserToUserResponse() {
    // Given
    UUID id = UUID.randomUUID();
    User user =
        User.builder()
            .id(id)
            .email("test@example.com")
            .firstName("John")
            .lastName("Doe")
            .phoneNumber("1234567890")
            .status(User.UserStatus.ACTIVE)
            .emailVerified(true)
            .phoneVerified(false)
            .roles(Set.of(User.UserRole.BUYER))
            .build();

    // When
    UserResponse response = mapper.toResponse(user);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(id);
    assertThat(response.getEmail()).isEqualTo("test@example.com");
    assertThat(response.getFirstName()).isEqualTo("John");
    assertThat(response.getLastName()).isEqualTo("Doe");
    assertThat(response.getPhoneNumber()).isEqualTo("1234567890");
    assertThat(response.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
    assertThat(response.getEmailVerified()).isTrue();
    assertThat(response.getPhoneVerified()).isFalse();
    assertThat(response.getRoles()).containsExactly(User.UserRole.BUYER);
    assertThat(response.getEnabled()).isTrue();
  }

  @Test
  void updateEntity_shouldUpdateUserFromRequest() {
    // Given
    User user =
        User.builder().firstName("OldFirst").lastName("OldLast").phoneNumber("OldPhone").build();

    UserUpdateRequest request = new UserUpdateRequest();
    request.setFirstName("NewFirst");
    request.setLastName("NewLast");
    request.setPhoneNumber("NewPhone");

    // When
    mapper.updateEntity(user, request);

    // Then
    assertThat(user.getFirstName()).isEqualTo("NewFirst");
    assertThat(user.getLastName()).isEqualTo("NewLast");
    assertThat(user.getPhoneNumber()).isEqualTo("NewPhone");
  }
}
