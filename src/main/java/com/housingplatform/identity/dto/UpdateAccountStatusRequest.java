package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.User;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Enable, disable or suspend a company account. */
@Data
public class UpdateAccountStatusRequest {

  @NotNull(message = "Status is required")
  private User.UserStatus status;
}
