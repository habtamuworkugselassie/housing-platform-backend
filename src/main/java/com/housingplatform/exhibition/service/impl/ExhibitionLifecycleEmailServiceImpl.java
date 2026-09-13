package com.housingplatform.exhibition.service.impl;

import com.housingplatform.exhibition.config.ExpoProperties;
import com.housingplatform.exhibition.domain.ExhibitionEmailKind;
import com.housingplatform.exhibition.domain.ExhibitionInterestEmail;
import com.housingplatform.exhibition.email.ExhibitionEmailDispatcher;
import com.housingplatform.exhibition.repository.ExhibitionInterestEmailRepository;
import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.exhibition.service.ExhibitionLifecycleEmailService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Decides who gets which lifecycle mail; {@link ExhibitionEmailDispatcher} does the sending.
 *
 * <p>Kept transaction-free on purpose. The loop can run for as long as a few hundred SMTP round
 * trips take, and holding one database transaction open across all of them would pin a connection
 * for minutes; each send commits on its own instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitionLifecycleEmailServiceImpl implements ExhibitionLifecycleEmailService {

  private final ExhibitionInterestRepository interestRepository;
  private final ExhibitionInterestEmailRepository emailRepository;
  private final ExhibitionEmailDispatcher dispatcher;
  private final ExpoProperties expo;

  @Override
  public void sendConfirmation(UUID interestId) {
    if (!expo.getLifecycleEmails().isEnabled()) {
      return;
    }
    dispatcher.dispatch(interestId, ExhibitionEmailKind.CONFIRMATION);
  }

  @Override
  public int sendDueReminders(LocalDate today) {
    if (!expo.getLifecycleEmails().isEnabled()) {
      return 0;
    }
    long daysUntilStart = ChronoUnit.DAYS.between(today, expo.getStartDate());
    Optional<ExhibitionEmailKind> due = ExhibitionEmailKind.reminderDueAt(daysUntilStart);
    if (due.isEmpty()) {
      // Nothing is due today, and nothing is owed for a day that has passed: "one week to go",
      // sent three days late, is worse than not sending it. A missed day stays missed.
      return 0;
    }
    return sendReminder(due.get());
  }

  @Override
  public int sendReminder(ExhibitionEmailKind kind) {
    if (!kind.isReminder()) {
      throw new IllegalArgumentException(kind + " is not a reminder");
    }
    Set<UUID> settled = emailRepository.findSettledInterestIds(kind);
    List<UUID> candidates = interestRepository.findReminderCandidateIds();

    // The quota is what the relay allows per day, and it is shared with the confirmations sent
    // that day, so it is measured from the log rather than counted per run. Start-of-day is taken
    // in the same clock the dispatcher stamps sentAt with, so the two always agree.
    LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
    long spent =
        emailRepository.countByStatusAndSentAtGreaterThanEqual(
            ExhibitionInterestEmail.Status.SENT, startOfToday);
    long remaining = Math.max(0, expo.getLifecycleEmails().getDailyQuota() - spent);

    int sent = 0;
    int outstanding = 0;
    for (UUID interestId : candidates) {
      if (settled.contains(interestId)) {
        continue;
      }
      outstanding++;
      // Past the quota we keep counting but stop sending, so the log can say how much is left
      // rather than only that we stopped.
      if (sent < remaining && dispatcher.dispatch(interestId, kind)) {
        sent++;
      }
    }

    if (outstanding > sent) {
      log.warn(
          "Lifecycle reminder {}: sent {} of {} outstanding; daily quota {} with {} already spent"
              + " today. The rest go out on the next hourly run if quota remains — a reminder"
              + " not finished by midnight is not sent late.",
          kind,
          sent,
          outstanding,
          expo.getLifecycleEmails().getDailyQuota(),
          spent);
    } else if (sent > 0) {
      log.info("Lifecycle reminder {}: sent {}", kind, sent);
    }
    return sent;
  }
}
