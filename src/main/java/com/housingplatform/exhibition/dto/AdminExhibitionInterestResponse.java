package com.housingplatform.exhibition.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Admin view of a public exhibition interest registration (org + lead details). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminExhibitionInterestResponse {

  private UUID id;
  private LocalDateTime createdAt;

  private String email;
  private String phoneNumber;
  private String interestType;
  private String company;
  private String message;

  private UUID organizationId;
  private String organizationName;
  private String organizationType;
  private String organizationStatus;

  private UUID sponsorshipId;
  private String sponsorshipPackageName;

  /** When set, admin has verified the exhibition registrant contact (email/phone). */
  private LocalDateTime contactVerifiedAt;

  /** Platform user id when a primary contact user exists for the linked organization. */
  private UUID primaryContactUserId;
}
