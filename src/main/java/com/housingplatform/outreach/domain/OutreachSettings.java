package com.housingplatform.outreach.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** The scheduler switch and caps, one row, edited from the admin page. */
@Entity
@Table(name = "outreach_settings")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class OutreachSettings extends BaseEntity {

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "daily_cap", nullable = false)
  private int dailyCap;

  @Column(name = "hourly_cap", nullable = false)
  private int hourlyCap;

  @Column(name = "send_window_start", nullable = false)
  private int sendWindowStart;

  @Column(name = "send_window_end", nullable = false)
  private int sendWindowEnd;

  @Column(name = "follow_up_after_days", nullable = false)
  private int followUpAfterDays;

  /** Email NEW contacts without an explicit queue step, oldest first, under the caps. */
  @Column(name = "auto_queue_new", nullable = false)
  private boolean autoQueueNew;
}
