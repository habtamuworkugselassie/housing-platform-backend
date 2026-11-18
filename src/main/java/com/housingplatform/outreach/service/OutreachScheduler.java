package com.housingplatform.outreach.service;

import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.domain.OutreachContact.Status;
import com.housingplatform.outreach.domain.OutreachMessage;
import com.housingplatform.outreach.dto.OutreachDtos.Stats;
import com.housingplatform.outreach.repository.OutreachContactRepository;
import com.housingplatform.outreach.repository.OutreachMessageRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives the automatic part of the sequence: sends due first emails and follow-ups inside the
 * working window under the daily and hourly caps, and checks the reply-to mailbox for answers and
 * opt-outs. Deliberately slow and small: this is one-to-one business mail, not a blast.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutreachScheduler {

  static final Pattern OPT_OUT =
      Pattern.compile(
          "no thanks|not interested|unsubscribe|remove (me|us)|stop (emailing|contacting)|do not (contact|email)|አልፈልግም|አያስፈልግ",
          Pattern.CASE_INSENSITIVE);

  private static final Set<String> FREE_MAIL =
      Set.of("gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "icloud.com", "ethionet.et");

  private final OutreachProperties props;
  private final OutreachContactRepository contacts;
  private final OutreachMessageRepository messages;
  private final OutreachService service;
  private final OutreachMailer mailer;
  private final OutreachTemplateRenderer renderer;
  private final OutreachReplyReader replies;
  private final ExhibitionInterestRepository interests;

  // ------------------------------------------------------------------ sending

  @Scheduled(fixedDelayString = "${app.outreach.tick-ms:300000}", initialDelayString = "60000")
  public void tick() {
    if (!props.isEnabled() || !mailer.configured()) return;
    if (!insideSendWindow()) return;
    int room = Math.min(capRemainingToday(), capRemainingThisHour());
    if (room <= 0) return;
    int batch = Math.min(room, 2);
    List<OutreachContact> due =
        contacts.findTop20ByStatusInAndNextActionAtLessThanEqualOrderByNextActionAtAsc(
            List.of(Status.QUEUED, Status.SENT), LocalDateTime.now());
    int sent = 0;
    for (OutreachContact c : due) {
      if (sent >= batch) break;
      try {
        if (c.getStatus() == Status.SENT && c.getStep() >= 1) {
          syncOne(c);
          if (c.getStatus() != Status.SENT) continue;
        }
        sendStep(c, c.getStep() == 0 ? 1 : 2);
        sent++;
      } catch (Exception e) {
        log.error("Outreach send to {} failed: {}", c.getEmail(), e.getMessage());
        c.setLastError(truncate(e.getMessage()));
        c.setNextActionAt(LocalDateTime.now().plusHours(6));
        contacts.save(c);
      }
    }
  }

  @Transactional
  public void sendStep(OutreachContact c, int step) throws Exception {
    OutreachTemplateRenderer.Rendered r = service.render(c, step);
    String inReplyTo =
        step == 2
            ? messages.findByContactIdOrderBySentAtAsc(c.getId()).stream()
                .map(OutreachMessage::getProviderMessageId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null)
            : null;
    String messageId =
        mailer.send(
            c.getEmail(),
            r.subject(),
            r.body(),
            c.getLocale(),
            renderer.registerUrl(c),
            inReplyTo,
            step == 1 ? OutreachMailer.Options.firstEmail() : OutreachMailer.Options.followUp());
    LocalDateTime now = LocalDateTime.now();
    messages.save(
        OutreachMessage.builder()
            .contact(c)
            .step(step)
            .subject(r.subject())
            .body(r.body())
            .providerMessageId(messageId)
            .sentAt(now)
            .build());
    c.setStep(step);
    c.setLastSentAt(now);
    c.setLastError(null);
    if (step == 1) {
      c.setStatus(Status.SENT);
      c.setNextActionAt(addBusinessDays(now, props.getFollowUpAfterDays()));
    } else {
      c.setStatus(Status.FOLLOWED_UP);
      c.setNextActionAt(null);
    }
    contacts.save(c);
    log.info("Outreach step {} sent to {} ({})", step, c.getEmail(), c.getName());
  }

  // ------------------------------------------------------------------ replies

  @Scheduled(fixedDelayString = "${app.outreach.sync-ms:1800000}", initialDelayString = "120000")
  public void syncReplies() {
    if (replies.configured()) {
      List<OutreachContact> open =
          contacts.findByStatusInAndLastSentAtIsNotNull(List.of(Status.SENT, Status.FOLLOWED_UP));
      for (OutreachContact c : open) {
        try {
          syncOne(c);
        } catch (Exception e) {
          log.warn("Reply check for {} failed: {}", c.getEmail(), e.getMessage());
        }
      }
    }
    markRegistered();
  }

  /** Any message from the contact since our last email counts as a reply, or an opt-out. */
  @Transactional
  public void syncOne(OutreachContact c) throws Exception {
    if (!replies.configured() || c.getLastSentAt() == null) return;
    applyReplies(c, replies.repliesFrom(c.getEmail(), c.getLastSentAt()));
  }

  public void applyReplies(OutreachContact c, List<OutreachReplyReader.Reply> found) {
    for (OutreachReplyReader.Reply r : found) {
      c.setStatus(OPT_OUT.matcher(r.text()).find() ? Status.OPTED_OUT : Status.REPLIED);
      c.setRepliedAt(r.receivedAt());
      c.setNextActionAt(null);
      contacts.save(c);
      log.info("Outreach contact {} -> {}", c.getEmail(), c.getStatus());
      return;
    }
  }

  /** A contact whose email (or company domain) registered interest on the portal is done. */
  @Transactional
  public void markRegistered() {
    List<OutreachContact> open =
        contacts.findByStatusInAndLastSentAtIsNotNull(
            List.of(Status.SENT, Status.FOLLOWED_UP, Status.REPLIED));
    if (open.isEmpty()) return;
    Set<String> registeredEmails = new HashSet<>();
    Set<String> registeredDomains = new HashSet<>();
    interests
        .findAll()
        .forEach(
            i -> {
              String e = i.getEmail() == null ? "" : i.getEmail().toLowerCase(Locale.ROOT);
              registeredEmails.add(e);
              String d = domainOf(e);
              if (d != null && !FREE_MAIL.contains(d)) registeredDomains.add(d);
            });
    for (OutreachContact c : open) {
      String e = c.getEmail().toLowerCase(Locale.ROOT);
      String d = domainOf(e);
      if (registeredEmails.contains(e) || (d != null && registeredDomains.contains(d))) {
        c.setStatus(Status.REGISTERED);
        c.setNextActionAt(null);
        contacts.save(c);
        log.info("Outreach contact {} registered on the portal", c.getEmail());
      }
    }
  }

  // ------------------------------------------------------------------ stats / helpers

  public Stats stats() {
    Map<String, Long> byStatus = new LinkedHashMap<>();
    for (Status s : Status.values()) byStatus.put(s.name(), 0L);
    for (Object[] row : contacts.countsByStatus())
      byStatus.put(((Status) row[0]).name(), (Long) row[1]);
    return Stats.builder()
        .byStatus(byStatus)
        .sentToday(sentToday())
        .sentLastHour(messages.countBySentAtGreaterThanEqual(LocalDateTime.now().minusHours(1)))
        .dailyCap(props.getDailyCap())
        .hourlyCap(props.getHourlyCap())
        .enabled(props.isEnabled())
        .mailConfigured(mailer.configured())
        .replyReaderConfigured(replies.configured())
        .insideSendWindow(insideSendWindow())
        .senderEmail(mailer.from())
        .replyTo(mailer.replyTo())
        .timezone(props.getTimezone())
        .sendWindowStart(props.getSendWindowStart())
        .sendWindowEnd(props.getSendWindowEnd())
        .followUpAfterDays(props.getFollowUpAfterDays())
        .build();
  }

  boolean insideSendWindow() {
    ZonedDateTime now = ZonedDateTime.now(ZoneId.of(props.getTimezone()));
    DayOfWeek d = now.getDayOfWeek();
    if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) return false;
    int h = now.getHour();
    return h >= props.getSendWindowStart() && h < props.getSendWindowEnd();
  }

  private long sentToday() {
    ZoneId zone = ZoneId.of(props.getTimezone());
    ZonedDateTime startOfDay = ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone);
    return messages.countBySentAtGreaterThanEqual(
        startOfDay.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
  }

  private int capRemainingToday() {
    return (int) Math.max(0, props.getDailyCap() - sentToday());
  }

  private int capRemainingThisHour() {
    return (int)
        Math.max(
            0,
            props.getHourlyCap()
                - messages.countBySentAtGreaterThanEqual(LocalDateTime.now().minusHours(1)));
  }

  public static LocalDateTime addBusinessDays(LocalDateTime from, int days) {
    LocalDate d = from.toLocalDate();
    int added = 0;
    while (added < days) {
      d = d.plusDays(1);
      if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) added++;
    }
    return d.atTime(from.toLocalTime());
  }

  static String domainOf(String email) {
    int at = email.indexOf('@');
    return at < 0 ? null : email.substring(at + 1);
  }

  private static String truncate(String s) {
    if (s == null) return null;
    return s.length() > 900 ? s.substring(0, 900) : s;
  }
}
