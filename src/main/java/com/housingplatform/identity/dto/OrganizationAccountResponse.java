package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.User;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One login belonging to a sponsor company, as shown on the admin Accounts panel. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationAccountResponse {

  private UUID id;
  private String email;
  private String firstName;
  private String lastName;
  private String phoneNumber;
  private User.UserStatus status;
  private Set<User.UserRole> roles;
  private Boolean emailVerified;

  /** True when this user is the organization's designated primary contact. */
  private Boolean primaryContact;

  private UUID organizationId;
  private String organizationName;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** Admin who provisioned the account (JPA auditing), when recorded. */
  private String createdBy;

  /** Admin who last changed the account (password reset, status change), when recorded. */
  private String updatedBy;
}
