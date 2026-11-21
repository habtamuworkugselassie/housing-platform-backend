package com.housingplatform.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Once a morning (Addis time): nudge reservations that have gone quiet for five business days. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationReminderJob {

  private final ReservationService service;

  @Scheduled(cron = "0 30 9 * * MON-FRI", zone = "Africa/Addis_Ababa")
  public void run() {
    try {
      int freed = service.expireBoothHolds();
      if (freed > 0) log.info("Booth holds expired on {} reservation(s)", freed);
      int n = service.sendDueReminders(5, 2);
      if (n > 0) log.info("Sent {} sponsorship reservation reminder(s)", n);
    } catch (Exception e) {
      log.error("Reservation reminders failed: {}", e.getMessage());
    }
  }
}
