package com.housingplatform.outreach.dto;

import com.housingplatform.outreach.domain.OutreachContact;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

public final class OutreachDtos {
  private OutreachDtos() {}

  @Data
  @Builder
  public static class ContactResponse {
    private UUID id;
    private String name;
    private String contactName;
    private String sector;
    private String templateKey;
    private String effectiveTemplateKey;
    private String email;
    private String phone;
    private String website;
    private String city;
    private String locale;
    private String confidence;
    private String sourceUrl;
    private String notes;
    private String hook;
    private String owner;
    private OutreachContact.Status status;
    private int step;
    private LocalDateTime nextActionAt;
    private LocalDateTime lastSentAt;
    private LocalDateTime repliedAt;
    private String threadId;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
  }

  @Data
  public static class UpdateContactRequest {
    private String contactName;
    private String templateKey;
    private String locale;
    private String hook;
    private String owner;
    private String notes;
    private String phone;
    private OutreachContact.Status status;
  }

  @Data
  public static class IdsRequest {
    private List<UUID> ids;
  }

  @Data
  @Builder
  public static class ImportResult {
    private int added;
    private int updated;
    private int skippedInvalid;
    private int skippedProtected;
    private List<String> problems;
  }

  @Data
  @Builder
  public static class PreviewResponse {
    private String to;
    private String subject;
    private String body;
    private String templateKey;
    private String locale;
  }

  @Data
  @Builder
  public static class TemplateResponse {
    private UUID id;
    private String templateKey;
    private String locale;
    private String subject;
    private String body;
  }

  @Data
  public static class TemplateRequest {
    private String subject;
    private String body;
  }

  @Data
  @Builder
  public static class Stats {
    private Map<String, Long> byStatus;
    private long sentToday;
    private long sentLastHour;
    private int dailyCap;
    private int hourlyCap;
    private boolean enabled;
    private boolean gmailConfigured;
    private boolean insideSendWindow;
    private String senderEmail;
    private String timezone;
    private int sendWindowStart;
    private int sendWindowEnd;
    private int followUpAfterDays;
  }

  @Data
  @Builder
  public static class MessageResponse {
    private UUID id;
    private int step;
    private String subject;
    private String body;
    private LocalDateTime sentAt;
  }

  @Data
  public static class SendTestRequest {
    private String to;
    private int step = 1;
  }
}
