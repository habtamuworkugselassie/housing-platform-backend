package com.housingplatform.visitor.service;

import com.housingplatform.visitor.domain.VisitorRegistration;
import com.housingplatform.visitor.repository.VisitorRegistrationRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * From the reminder date (two days before the expo by default) until the expo ends, every
 * registered visitor who has not had one gets the reminder with their badge, in batches each hour.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VisitorReminderJob {

  private final VisitorRegistrationRepository repository;
  private final VisitorBadgeMailService mail;

  @Value("${app.visitor.reminder-from:2027-01-07}")
  private String reminderFrom;

  @Value("${app.visitor.reminder-until:2027-01-12}")
  private String reminderUntil;

  @Scheduled(cron = "0 15 8-18 * * *", zone = "Africa/Addis_Ababa")
  public void run() {
    LocalDate today = LocalDate.now(ZoneId.of("Africa/Addis_Ababa"));
    if (today.isBefore(LocalDate.parse(reminderFrom))
        || today.isAfter(LocalDate.parse(reminderUntil))) return;
    List<VisitorRegistration> due =
        repository.findTop200ByStatusAndReminderSentAtIsNullOrderByCreatedAtAsc(
            VisitorRegistration.Status.REGISTERED);
    int sent = 0;
    for (VisitorRegistration v : due) {
      try {
        if (mail.sendReminder(v)) sent++;
      } catch (Exception e) {
        log.warn("Visitor reminder to {} failed: {}", v.getEmail(), e.getMessage());
      }
    }
    if (sent > 0) log.info("Sent {} visitor reminder(s)", sent);
  }
}
