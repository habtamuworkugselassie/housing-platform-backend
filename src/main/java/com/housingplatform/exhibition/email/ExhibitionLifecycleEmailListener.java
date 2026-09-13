package com.housingplatform.exhibition.email;

import com.housingplatform.exhibition.service.ExhibitionLifecycleEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the confirmation once the registration is safely committed.
 *
 * <p>After commit, not during: a mail is not rollback-able, and telling someone "you are
 * registered" for a row that then failed to save is the one mistake this flow must never make.
 *
 * <p>Async, and swallowing its own failures, because a mail server having a bad afternoon must not
 * turn a successful registration into an error for the person who just filled in the form. A
 * failure is recorded in the send log and stays retryable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExhibitionLifecycleEmailListener {

  private final ExhibitionLifecycleEmailService lifecycleEmailService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRegistered(ExhibitionInterestRegisteredEvent event) {
    try {
      lifecycleEmailService.sendConfirmation(event.interestId());
    } catch (Exception e) {
      log.error(
          "Confirmation mail for interest {} failed: {}", event.interestId(), e.getMessage(), e);
    }
  }
}
