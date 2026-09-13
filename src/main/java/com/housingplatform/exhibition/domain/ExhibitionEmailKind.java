package com.housingplatform.exhibition.domain;

import java.util.Optional;

/**
 * The lifecycle mails a registrant can receive, and — for the reminder series — how many days
 * before the expo opens each one goes out.
 *
 * <p>The schedule lives here rather than in configuration on purpose. The send log keys on this
 * name, so a reminder's identity and its timing have to agree: moving "30 days" into a property
 * would let the two drift, and a {@code REMINDER_T30} row that actually went out at T-14 makes the
 * log a record of nothing. Change the offset here and the log, the scheduler and the copy all move
 * together.
 */
public enum ExhibitionEmailKind {

  /** Sent immediately after registration. Transactional: an unsubscribe does not suppress it. */
  CONFIRMATION(null),

  REMINDER_T30(30),
  REMINDER_T7(7),
  REMINDER_T1(1);

  private final Integer daysBeforeStart;

  ExhibitionEmailKind(Integer daysBeforeStart) {
    this.daysBeforeStart = daysBeforeStart;
  }

  public Integer getDaysBeforeStart() {
    return daysBeforeStart;
  }

  public boolean isReminder() {
    return daysBeforeStart != null;
  }

  /** The reminder due when the expo is exactly {@code daysUntilStart} days away, if any. */
  public static Optional<ExhibitionEmailKind> reminderDueAt(long daysUntilStart) {
    for (ExhibitionEmailKind kind : values()) {
      if (kind.isReminder() && kind.daysBeforeStart == daysUntilStart) {
        return Optional.of(kind);
      }
    }
    return Optional.empty();
  }
}
