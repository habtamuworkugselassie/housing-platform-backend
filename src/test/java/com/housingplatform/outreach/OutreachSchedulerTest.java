package com.housingplatform.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.repository.OutreachContactRepository;
import com.housingplatform.outreach.repository.OutreachMessageRepository;
import com.housingplatform.outreach.service.GmailClient;
import com.housingplatform.outreach.service.OutreachProperties;
import com.housingplatform.outreach.service.OutreachScheduler;
import com.housingplatform.outreach.service.OutreachService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OutreachSchedulerTest {

  private static OutreachScheduler scheduler(
      GmailClient gmail, OutreachContactRepository contacts) {
    return new OutreachScheduler(
        new OutreachProperties(),
        contacts,
        mock(OutreachMessageRepository.class),
        mock(OutreachService.class),
        gmail,
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
            .threadId("t1")
            .build();
    c.setNextActionAt(LocalDateTime.now().plusDays(3));
    return c;
  }

  @Test
  void followUpLandsOnABusinessDay() {
    LocalDateTime friday = LocalDateTime.of(2026, 11, 20, 10, 0); // Friday
    LocalDateTime due =
        (LocalDateTime)
            ReflectionTestUtils.invokeMethod(OutreachScheduler.class, "addBusinessDays", friday, 5);
    assertThat(due.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    assertThat(due.toLocalDate()).isEqualTo(friday.toLocalDate().plusDays(7));
  }

  @Test
  void aReplyStopsTheSequence() throws Exception {
    GmailClient gmail = mock(GmailClient.class);
    when(gmail.isOwnAddress("Ethio Build Connect <ethiobuildconnectexpo@gmail.com>"))
        .thenReturn(true);
    when(gmail.thread("t1"))
        .thenReturn(
            List.of(
                new GmailClient.ThreadMessage(
                    "Ethio Build Connect <ethiobuildconnectexpo@gmail.com>",
                    Instant.now().minusSeconds(600),
                    "Dear team, ..."),
                new GmailClient.ThreadMessage(
                    "Sales <sales@noah.et>", Instant.now(), "Thanks, let us talk next week.")));
    OutreachContactRepository contacts = mock(OutreachContactRepository.class);
    OutreachContact c = contact();
    scheduler(gmail, contacts).syncOne(c);
    assertThat(c.getStatus()).isEqualTo(OutreachContact.Status.REPLIED);
    assertThat(c.getNextActionAt()).isNull();
    assertThat(c.getRepliedAt()).isNotNull();
  }

  @Test
  void optOutAndBounceAreRecognised() throws Exception {
    GmailClient gmail = mock(GmailClient.class);
    when(gmail.thread("t1"))
        .thenReturn(
            List.of(
                new GmailClient.ThreadMessage(
                    "x@noah.et", Instant.now(), "No thanks, please remove us.")));
    OutreachContact c = contact();
    scheduler(gmail, mock(OutreachContactRepository.class)).syncOne(c);
    assertThat(c.getStatus()).isEqualTo(OutreachContact.Status.OPTED_OUT);

    when(gmail.thread("t1"))
        .thenReturn(
            List.of(
                new GmailClient.ThreadMessage(
                    "Mail Delivery Subsystem <mailer-daemon@googlemail.com>",
                    Instant.now(),
                    "Address not found")));
    OutreachContact b = contact();
    scheduler(gmail, mock(OutreachContactRepository.class)).syncOne(b);
    assertThat(b.getStatus()).isEqualTo(OutreachContact.Status.BOUNCED);

    when(gmail.thread("t1"))
        .thenReturn(List.of(new GmailClient.ThreadMessage("x@noah.et", Instant.now(), "አልፈልግም")));
    OutreachContact a = contact();
    scheduler(gmail, mock(OutreachContactRepository.class)).syncOne(a);
    assertThat(a.getStatus()).isEqualTo(OutreachContact.Status.OPTED_OUT);
  }

  @Test
  void nothingIsSentWhenDisabledOrUnconfigured() {
    GmailClient gmail = mock(GmailClient.class);
    when(gmail.configured()).thenReturn(false);
    OutreachContactRepository contacts = mock(OutreachContactRepository.class);
    scheduler(gmail, contacts).tick();
    org.mockito.Mockito.verifyNoInteractions(contacts);
  }
}
