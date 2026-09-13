package com.housingplatform.exhibition.email;

import com.housingplatform.exhibition.config.ExpoProperties;
import com.housingplatform.exhibition.domain.ExhibitionEmailKind;
import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.domain.ExhibitionInterestEmail;
import com.housingplatform.exhibition.repository.ExhibitionInterestEmailRepository;
import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends one lifecycle mail to one registrant, and records that it did.
 *
 * <p>Separate from the service that loops over registrants so that each send gets its own
 * transaction: a bounce, a rejected address or an SMTP timeout on the fortieth lead must not roll
 * back the log of the thirty-nine already mailed, or the next run would mail them all again.
 *
 * <p>The send log is written in the same transaction as the send decision, and the unique index on
 * {@code (interest_id, email_kind)} is the backstop if two runs ever overlap.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExhibitionEmailDispatcher {

  private static final int MAX_DETAIL = 500;

  private final ExhibitionInterestRepository interestRepository;
  private final ExhibitionInterestEmailRepository emailRepository;
  private final ExhibitionEmailComposer composer;
  private final JavaMailSender mailSender;
  private final ExpoProperties expo;

  @Value("${spring.mail.username:}")
  private String fromEmail;

  /**
   * @return true when a mail actually went out, false when it was already settled, suppressed or
   *     failed. The caller counts these to decide when it has hit the per-run ceiling, so only a
   *     real send counts against the quota.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean dispatch(UUID interestId, ExhibitionEmailKind kind) {
    ExhibitionInterest interest = interestRepository.findById(interestId).orElse(null);
    if (interest == null) {
      log.warn("Lifecycle mail {} skipped: interest {} no longer exists", kind, interestId);
      return false;
    }

    ExhibitionInterestEmail record =
        emailRepository.findByInterestIdAndKind(interestId, kind).orElse(null);
    if (record != null && record.getStatus() != ExhibitionInterestEmail.Status.FAILED) {
      return false;
    }
    if (record == null) {
      record =
          ExhibitionInterestEmail.builder()
              .interest(interest)
              .kind(kind)
              .recipient(interest.getEmail())
              .status(ExhibitionInterestEmail.Status.SENT)
              .attempts(0)
              .build();
    }
    record.setAttempts(record.getAttempts() + 1);

    // Checked here rather than only in the query that selected this lead: an opt-out can land
    // between the daily job building its list and this row being reached.
    if (kind.isReminder() && interest.isUnsubscribed()) {
      return save(record, ExhibitionInterestEmail.Status.SUPPRESSED, "registrant unsubscribed");
    }

    ExhibitionEmailContent content = composer.compose(interest, kind);

    if (fromEmail == null || fromEmail.isBlank()) {
      // Same fail-soft contract the password-reset mail has: on a machine with no SMTP account
      // the mail is logged instead of sent, so local and staging work without a mail server.
      log.warn(
          "Mail not configured (spring.mail.username empty). {} for {} would read:\n{}\n{}",
          kind,
          interest.getEmail(),
          content.subject(),
          content.body());
      return save(record, ExhibitionInterestEmail.Status.SUPPRESSED, "mail not configured");
    }

    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromEmail);
      message.setTo(interest.getEmail());
      String replyTo = expo.getLifecycleEmails().getReplyTo();
      if (replyTo != null && !replyTo.isBlank()) {
        message.setReplyTo(replyTo);
      }
      message.setSubject(content.subject());
      message.setText(content.body());
      mailSender.send(message);
    } catch (Exception e) {
      log.error("Lifecycle mail {} to {} failed: {}", kind, interest.getEmail(), e.getMessage(), e);
      save(record, ExhibitionInterestEmail.Status.FAILED, e.getMessage());
      return false;
    }

    record.setSentAt(LocalDateTime.now());
    save(record, ExhibitionInterestEmail.Status.SENT, null);
    log.info("Lifecycle mail {} sent to {}", kind, interest.getEmail());
    return true;
  }

  private boolean save(
      ExhibitionInterestEmail record, ExhibitionInterestEmail.Status status, String detail) {
    record.setStatus(status);
    record.setDetail(truncate(detail));
    try {
      emailRepository.saveAndFlush(record);
    } catch (DataIntegrityViolationException e) {
      // The unique index fired, so a concurrent run already logged this mail. Losing the race is
      // the correct outcome — better a log row we did not write than a second mail to the reader.
      log.warn(
          "Lifecycle mail {} for interest {} was already logged by a concurrent run",
          record.getKind(),
          record.getInterest().getId());
      return false;
    }
    return status == ExhibitionInterestEmail.Status.SENT;
  }

  private static String truncate(String detail) {
    if (detail == null) {
      return null;
    }
    return detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
  }
}
