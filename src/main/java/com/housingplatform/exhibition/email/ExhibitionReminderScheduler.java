package com.housingplatform.exhibition.email;

import com.housingplatform.exhibition.config.ExpoProperties;
import com.housingplatform.exhibition.service.ExhibitionLifecycleEmailService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs once a day and sends whichever reminder is due.
 *
 * <p>Once a day rather than hourly because "how many days until the expo" only changes at midnight,
 * and because a job that can only decide one thing per day should only be given the chance to get
 * it wrong once. The send log makes a second run on the same day harmless, so an operator can
 * safely trigger a catch-up by hand.
 *
 * <p>Today is read in the expo's own timezone. A server in UTC crosses midnight three hours after
 * Addis Ababa does, and the day-before mail is the one where that difference would show.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExhibitionReminderScheduler {

  private final ExhibitionLifecycleEmailService lifecycleEmailService;
  private final ExpoProperties expo;

  @Scheduled(
      cron = "${app.expo.lifecycle-emails.cron:0 0 9 * * *}",
      zone = "${app.expo.timezone:Africa/Addis_Ababa}")
  public void sendDueReminders() {
    LocalDate today = LocalDate.now(ZoneId.of(expo.getTimezone()));
    try {
      lifecycleEmailService.sendDueReminders(today);
    } catch (Exception e) {
      log.error("Expo reminder run for {} failed: {}", today, e.getMessage(), e);
    }
  }
}
