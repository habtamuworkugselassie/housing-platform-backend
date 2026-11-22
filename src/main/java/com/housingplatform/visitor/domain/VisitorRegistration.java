package com.housingplatform.visitor.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** A visitor's expo badge: the code is what the QR carries and what the door scans. */
@Entity
@Table(name = "visitor_registration")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VisitorRegistration extends BaseEntity {

  public enum Status {
    REGISTERED,
    CHECKED_IN,
    CANCELLED
  }

  public enum VisitorType {
    HOMEBUYER,
    INVESTOR,
    DIASPORA,
    DEVELOPER,
    CONTRACTOR,
    SUPPLIER,
    BANK_FINANCE,
    ARCHITECT_CONSULTANT,
    GOVERNMENT,
    MEDIA,
    STUDENT,
    OTHER
  }

  @Column(nullable = false, length = 16)
  private String code;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(nullable = false)
  private String email;

  @Column(length = 50)
  private String phone;

  @Column(length = 200)
  private String organization;

  @Enumerated(EnumType.STRING)
  @Column(name = "visitor_type", nullable = false, length = 40)
  private VisitorType visitorType;

  @Column(length = 1000)
  private String interests;

  @Column(nullable = false, length = 8)
  private String locale;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(name = "checked_in_at")
  private LocalDateTime checkedInAt;

  @Column(name = "checked_in_by")
  private String checkedInBy;

  @Column(length = 40)
  private String source;

  @Column(name = "badge_sent_at")
  private LocalDateTime badgeSentAt;

  @Column(name = "reminder_sent_at")
  private LocalDateTime reminderSentAt;
}
