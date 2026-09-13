package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.domain.ExhibitionEmailKind;
import java.time.LocalDate;
import java.util.UUID;

/** Confirmation and reminder mail for expo registrants. */
public interface ExhibitionLifecycleEmailService {

  /** Sends the registration confirmation, unless it has already been sent. */
  void sendConfirmation(UUID interestId);

  /**
   * Sends whichever reminder falls due on {@code today}, to everyone who has not already had it.
   *
   * @return how many mails actually went out
   */
  int sendDueReminders(LocalDate today);

  /** Sends one named reminder regardless of the date. For an operator catching up by hand. */
  int sendReminder(ExhibitionEmailKind kind);
}
