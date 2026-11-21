package com.housingplatform.visitor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

public final class VisitorDtos {
  private VisitorDtos() {}

  @Data
  public static class RegisterRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 200)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    @Size(max = 50)
    private String phone;

    @Size(max = 200)
    private String organization;

    /** One of VisitorRegistration.VisitorType; unknown values become OTHER. */
    @Size(max = 40)
    private String visitorType;

    @Size(max = 1000)
    private String interests;

    @Size(max = 8)
    private String locale;

    @Size(max = 40)
    private String source;

    /** Honeypot: humans never see it, bots fill it. */
    private String website;
  }

  /** What the visitor (or anyone holding the code) may see. */
  @Value
  @Builder
  public static class Badge {
    String code;
    String fullName;
    String organization;
    String visitorType;
    String status;
    String locale;
    LocalDateTime checkedInAt;
    LocalDateTime createdAt;
    String badgeUrl;
    String qrUrl;
    boolean alreadyRegistered;
  }

  @Value
  @Builder
  public static class AdminRow {
    UUID id;
    String code;
    String fullName;
    String email;
    String phone;
    String organization;
    String visitorType;
    String interests;
    String locale;
    String status;
    String source;
    LocalDateTime checkedInAt;
    String checkedInBy;
    LocalDateTime badgeSentAt;
    LocalDateTime createdAt;
  }

  @Value
  @Builder
  public static class Stats {
    long total;
    long checkedIn;
    long today;
    long checkedInToday;
    long last7Days;
    Map<String, Long> byType;
    List<DayCount> byDay;
  }

  public record DayCount(String day, long count) {}

  @Data
  public static class CheckInRequest {
    /** Badge code, or the full badge URL as a phone camera reads it. */
    private String code;
  }
}
