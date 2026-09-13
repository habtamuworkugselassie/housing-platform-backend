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
  private String partnerRole;
  private String visibilityScope;
  private String contributionMode;
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

  /**
   * Where this lead came from. Surfaced to admins because the point of capturing it is that someone
   * can look at the list and see which campaign is producing exhibitors rather than only visitors —
   * a split Analytics cannot show, since it never sees registrants who declined cookies.
   */
  private String utmSource;

  private String utmMedium;

  private String utmCampaign;

  private String utmTerm;

  private String utmContent;

  private String referrer;

  private String landingPath;

  /** Set when the registrant opted out of the reminder series. */
  private LocalDateTime unsubscribedAt;

  /** Platform user id when a primary contact user exists for the linked organization. */
  private UUID primaryContactUserId;
}
