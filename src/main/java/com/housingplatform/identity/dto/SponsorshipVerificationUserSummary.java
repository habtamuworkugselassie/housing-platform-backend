package com.housingplatform.identity.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Primary contact or super agent user to verify for a sponsorship application (admin review). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SponsorshipVerificationUserSummary {
  private UUID id;
  private String email;
  private String firstName;
  private String lastName;
  private String phoneNumber;
}
