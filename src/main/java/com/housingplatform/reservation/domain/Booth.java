package com.housingplatform.reservation.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** One 3×3 m booth on the hall plan. Held while a reservation is open, sold once it is paid. */
@Entity
@Table(name = "booth")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Booth extends BaseEntity {

  public enum Type {
    STANDARD,
    CORNER,
    PREMIUM
  }

  public enum Status {
    AVAILABLE,
    HELD,
    SOLD,
    BLOCKED
  }

  @Column(nullable = false, length = 10)
  private String code;

  @Column(nullable = false, length = 4)
  private String block;

  @Column(name = "map_col", nullable = false)
  private int mapCol;

  @Column(name = "map_row", nullable = false)
  private int mapRow;

  @Enumerated(EnumType.STRING)
  @Column(name = "booth_type", nullable = false, length = 20)
  private Type boothType;

  @Column(name = "size_sqm", nullable = false)
  private int sizeSqm;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reservation_id")
  private SponsorshipReservation reservation;

  @Column(name = "hold_expires_at")
  private LocalDateTime holdExpiresAt;

  @Column(length = 500)
  private String notes;
}
