package com.housingplatform.identity.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
  private String accessToken;
  private String tokenType;
  private Long expiresIn; // seconds
  private String refreshToken;
  private UUID userId;
  private String email;
  private String firstName;
  private String lastName;
  private List<String> scopes;
  private List<String> roles;
}
