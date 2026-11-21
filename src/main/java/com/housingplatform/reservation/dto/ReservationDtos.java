package com.housingplatform.reservation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

public final class ReservationDtos {
  private ReservationDtos() {}

  @Data
  public static class ReserveRequest {
    /** Package id from /sponsorships/active. */
    private UUID sponsorshipId;

    @NotBlank(message = "Company is required")
    @Size(max = 200)
    private String company;

    @NotBlank(message = "Contact name is required")
    @Size(max = 200)
    private String contactName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    @Size(max = 50)
    private String phone;

    @Size(max = 40)
    private String tin;

    @Size(max = 200)
    private String boothPreference;

    @Size(max = 4000)
    private String message;

    @Size(max = 8)
    private String locale;

    @Size(max = 40)
    private String source;

    /** Booth codes the sponsor would like (optional, at most the package's booth count). */
    private List<String> boothCodes;

    /** Honeypot. */
    private String website;
  }

  @Value
  @Builder
  public static class BoothView {
    String code;
    String block;
    int col;
    int row;
    String boothType;
    int sizeSqm;
    String status;
    UUID reservationId;
    LocalDateTime holdExpiresAt;
    String notes;
  }

  /** Public map payload: booths without who holds them, plus counts. */
  @Value
  @Builder
  public static class BoothMap {
    List<BoothView> booths;
    Map<String, Long> counts;
    int holdBusinessDays;
  }

  @Data
  public static class BoothUpdate {
    private String status;
    private String boothType;
    private String notes;
  }

  @Data
  public static class BoothAssign {
    private List<String> codes;
  }

  @Value
  @Builder
  public static class Confirmation {
    String reference;
    String packageName;
    BigDecimal amount;
    String currency;
    String status;
    String email;
    List<BankAccount> bankAccounts;
    String payee;
    List<String> boothCodes;
    int boothsIncluded;
  }

  public record BankAccount(String bank, String number) {}

  @Value
  @Builder
  public static class AdminRow {
    UUID id;
    String reference;
    UUID sponsorshipId;
    String packageName;
    String packageType;
    BigDecimal amount;
    String currency;
    String company;
    String contactName;
    String email;
    String phone;
    String tin;
    String boothPreference;
    String message;
    String locale;
    String status;
    BigDecimal amountPaid;
    String adminNotes;
    String assignedTo;
    String source;
    LocalDateTime agreementSentAt;
    LocalDateTime signedAt;
    LocalDateTime paidAt;
    LocalDateTime lastReminderAt;
    int reminderCount;
    LocalDateTime statusChangedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    List<String> boothCodes;
    int boothsIncluded;
  }

  @Data
  public static class AdminUpdate {
    private String status;
    private BigDecimal amountPaid;
    private String adminNotes;
    private String assignedTo;
    private String boothPreference;
    private BigDecimal amount;
  }

  @Value
  @Builder
  public static class Pipeline {
    long openCount;
    BigDecimal openValue;
    BigDecimal paidValue;
    Map<String, StatusTotal> byStatus;
    Map<String, Long> booths;
  }

  public record StatusTotal(long count, BigDecimal value, BigDecimal paid) {}
}
