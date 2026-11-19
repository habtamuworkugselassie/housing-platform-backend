package com.housingplatform.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.domain.OutreachSettings;
import com.housingplatform.outreach.repository.OutreachContactRepository;
import com.housingplatform.outreach.repository.OutreachMessageRepository;
import com.housingplatform.outreach.service.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutreachSchedulerTest {

  private static OutreachScheduler scheduler(
      OutreachMailer mailer, OutreachContactRepository contacts) {
    OutreachProperties props = new OutreachProperties();
    OutreachSettingsService settings = mock(OutreachSettingsService.class);
    when(settings.current())
        .thenReturn(
            OutreachSettings.builder()
                .enabled(false)
                .dailyCap(30)
                .hourlyCap(8)
                .sendWindowStart(9)
                .sendWindowEnd(17)
                .followUpAfterDays(5)
                .build());
    return new OutreachScheduler(
        props,
        settings,
        contacts,
        mock(OutreachMessageRepository.class),
        mock(OutreachService.class),
        mailer,
        new OutreachTemplateRenderer(props),
        mock(OutreachReplyReader.class),
        mock(ExhibitionInterestRepository.class));
  }

  private static OutreachContact contact() {
    OutreachContact c =
        OutreachContact.builder()
            .name("Noah")
            .sector("Real estate developer")
            .email("info@noah.et")
            .status(OutreachContact.Status.SENT)
            .step(1)
            .build();
    c.setLastSentAt(LocalDateTime.now().minusDays(2));
    c.setNextActionAt(LocalDateTime.now().plusDays(3));
    return c;
  }

  @Test
  void followUpLandsOnABusinessDay() {
    LocalDateTime friday = LocalDateTime.of(2026, 11, 20, 10, 0);
    LocalDateTime due = OutreachScheduler.addBusinessDays(friday, 5);
    assertThat(due.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    assertThat(due.toLocalDate()).isEqualTo(friday.toLocalDate().plusDays(7));
  }

  @Test
  void aReplyStopsTheSequenceAndAnOptOutIsRecognised() {
    OutreachScheduler s =
        scheduler(mock(OutreachMailer.class), mock(OutreachContactRepository.class));
    OutreachContact replied = contact();
    s.applyReplies(
        replied,
        List.of(
            new OutreachReplyReader.Reply(
                "sales@noah.et", LocalDateTime.now(), "Thanks, let us talk next week.")));
    assertThat(replied.getStatus()).isEqualTo(OutreachContact.Status.REPLIED);
    assertThat(replied.getNextActionAt()).isNull();
    assertThat(replied.getRepliedAt()).isNotNull();

    OutreachContact en = contact();
    s.applyReplies(
        en,
        List.of(
            new OutreachReplyReader.Reply(
                "x@noah.et", LocalDateTime.now(), "No thanks, please remove us.")));
    assertThat(en.getStatus()).isEqualTo(OutreachContact.Status.OPTED_OUT);

    OutreachContact am = contact();
    s.applyReplies(
        am, List.of(new OutreachReplyReader.Reply("x@noah.et", LocalDateTime.now(), "አልፈልግም")));
    assertThat(am.getStatus()).isEqualTo(OutreachContact.Status.OPTED_OUT);

    OutreachContact none = contact();
    s.applyReplies(none, List.of());
    assertThat(none.getStatus()).isEqualTo(OutreachContact.Status.SENT);
  }

  @Test
  void nothingIsSentWhenDisabledOrUnconfigured() {
    OutreachMailer mailer = mock(OutreachMailer.class);
    when(mailer.configured()).thenReturn(false);
    OutreachContactRepository contacts = mock(OutreachContactRepository.class);
    scheduler(mailer, contacts).tick();
    verifyNoInteractions(contacts);
  }

  @Test
  void promotesNewContactsOldestFirstWhenAutoQueueIsOn() {
    OutreachContactRepository contacts = mock(OutreachContactRepository.class);
    OutreachContact a =
        OutreachContact.builder()
            .name("A")
            .email("a@x.et")
            .status(OutreachContact.Status.NEW)
            .step(0)
            .build();
    OutreachContact b =
        OutreachContact.builder()
            .name("B")
            .email("b@x.et")
            .status(OutreachContact.Status.NEW)
            .step(0)
            .build();
    OutreachContact c =
        OutreachContact.builder()
            .name("C")
            .email("c@x.et")
            .status(OutreachContact.Status.NEW)
            .step(0)
            .build();
    when(contacts.findTop20ByStatusAndStepOrderByCreatedAtAsc(OutreachContact.Status.NEW, 0))
        .thenReturn(List.of(a, b, c));
    scheduler(mock(OutreachMailer.class), contacts).promoteNew(2);
    assertThat(a.getStatus()).isEqualTo(OutreachContact.Status.QUEUED);
    assertThat(a.getNextActionAt()).isNotNull();
    assertThat(b.getStatus()).isEqualTo(OutreachContact.Status.QUEUED);
    assertThat(c.getStatus()).isEqualTo(OutreachContact.Status.NEW);
  }
}
