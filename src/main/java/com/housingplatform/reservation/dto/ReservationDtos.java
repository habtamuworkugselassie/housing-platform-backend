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

    /** Honeypot. */
    private String website;
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
  }

  public record StatusTotal(long count, BigDecimal value, BigDecimal paid) {}
}
