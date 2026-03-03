package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.User;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
  private UUID id;
  private String email;
  private String firstName;
  private String lastName;
  private String phoneNumber;
  private User.UserStatus status;
  private Boolean emailVerified;
  private Boolean phoneVerified;
  private Set<User.UserRole> roles;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private UUID organizationId;

  // Computed property for backward compatibility with frontend
  // This will be serialized as "enabled" in JSON
  public Boolean getEnabled() {
    return status != null && status == User.UserStatus.ACTIVE;
  }

  // Setter for backward compatibility (maps enabled to status)
  public void setEnabled(Boolean enabled) {
    if (enabled != null) {
      this.status = enabled ? User.UserStatus.ACTIVE : User.UserStatus.INACTIVE;
    }
  }
}
