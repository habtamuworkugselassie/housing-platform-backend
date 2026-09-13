package com.housingplatform.exhibition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.exhibition.config.ExpoProperties;
import com.housingplatform.exhibition.domain.ExhibitionEmailKind;
import com.housingplatform.exhibition.email.ExhibitionEmailDispatcher;
import com.housingplatform.exhibition.repository.ExhibitionInterestEmailRepository;
import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import com.housingplatform.exhibition.service.impl.ExhibitionLifecycleEmailServiceImpl;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Who gets which reminder, and on which day. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExhibitionLifecycleEmailServiceTest {

  private static final LocalDate EXPO_OPENS = LocalDate.of(2026, 11, 16);

  @Mock private ExhibitionInterestRepository interestRepository;
  @Mock private ExhibitionInterestEmailRepository emailRepository;
  @Mock private ExhibitionEmailDispatcher dispatcher;

  private ExpoProperties expo;
  private ExhibitionLifecycleEmailServiceImpl service;

  @BeforeEach
  void setUp() {
    expo = new ExpoProperties();
    expo.setStartDate(EXPO_OPENS);
    expo.setEndDate(LocalDate.of(2026, 11, 18));
    service =
        new ExhibitionLifecycleEmailServiceImpl(
            interestRepository, emailRepository, dispatcher, expo);

    when(emailRepository.findSettledInterestIds(any())).thenReturn(Set.of());
    when(interestRepository.findReminderCandidateIds()).thenReturn(List.of());
    when(dispatcher.dispatch(any(), any())).thenReturn(true);
  }

  private List<UUID> candidates(int count) {
    List<UUID> ids = IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    when(interestRepository.findReminderCandidateIds()).thenReturn(ids);
    return ids;
  }

  @ParameterizedTest
  @CsvSource({"2026-10-17, REMINDER_T30", "2026-11-09, REMINDER_T7", "2026-11-15, REMINDER_T1"})
  void eachReminderGoesOutOnItsOwnDay(String today, ExhibitionEmailKind expected) {
    candidates(1);

    service.sendDueReminders(LocalDate.parse(today));

    verify(dispatcher).dispatch(any(), eq(expected));
  }

  @ParameterizedTest
  @CsvSource({"2026-10-18", "2026-11-08", "2026-11-14", "2026-09-01"})
  void anOrdinaryDaySendsNothing(String today) {
    candidates(5);

    assertThat(service.sendDueReminders(LocalDate.parse(today))).isZero();
    verify(dispatcher, never()).dispatch(any(), any());
  }

  @ParameterizedTest
  @CsvSource({"2026-11-16", "2026-11-17", "2026-11-19", "2027-01-05"})
  void nothingIsSentOnceTheExpoHasOpened(String today) {
    candidates(5);

    assertThat(service.sendDueReminders(LocalDate.parse(today))).isZero();
    verify(dispatcher, never()).dispatch(any(), any());
  }

  @Test
  void aMissedDayIsNotMadeUpForLater() {
    // "One week to go", sent four days late, is worse than silence — it tells the reader we are
    // not paying attention, and it gives them the wrong date to plan around.
    candidates(3);

    assertThat(service.sendDueReminders(LocalDate.of(2026, 11, 13))).isZero();
    verify(dispatcher, never()).dispatch(any(), eq(ExhibitionEmailKind.REMINDER_T7));
  }

  @Test
  void anyoneAlreadyMailedIsSkipped() {
    List<UUID> ids = candidates(4);
    when(emailRepository.findSettledInterestIds(ExhibitionEmailKind.REMINDER_T30))
        .thenReturn(Set.of(ids.get(0), ids.get(2)));

    assertThat(service.sendDueReminders(LocalDate.of(2026, 10, 17))).isEqualTo(2);
    verify(dispatcher).dispatch(ids.get(1), ExhibitionEmailKind.REMINDER_T30);
    verify(dispatcher).dispatch(ids.get(3), ExhibitionEmailKind.REMINDER_T30);
    verify(dispatcher, never()).dispatch(ids.get(0), ExhibitionEmailKind.REMINDER_T30);
  }

  @Test
  void theRunStopsAtTheQuotaAndLeavesTheRestForTomorrow() {
    candidates(10);
    expo.getLifecycleEmails().setMaxPerRun(3);

    assertThat(service.sendDueReminders(LocalDate.of(2026, 10, 17))).isEqualTo(3);
    verify(dispatcher, times(3)).dispatch(any(), eq(ExhibitionEmailKind.REMINDER_T30));
  }

  @Test
  void aSuppressedOrFailedSendDoesNotEatSomeoneElsesQuota() {
    // The ceiling exists to protect an SMTP quota, and a mail that was never handed to the server
    // consumed none of it. Counting it would silently shrink the run.
    candidates(4);
    expo.getLifecycleEmails().setMaxPerRun(2);
    when(dispatcher.dispatch(any(), any())).thenReturn(false, false, true, true);

    assertThat(service.sendDueReminders(LocalDate.of(2026, 10, 17))).isEqualTo(2);
    verify(dispatcher, times(4)).dispatch(any(), eq(ExhibitionEmailKind.REMINDER_T30));
  }

  @Test
  void theMasterSwitchStopsEverything() {
    candidates(5);
    expo.getLifecycleEmails().setEnabled(false);

    assertThat(service.sendDueReminders(LocalDate.of(2026, 10, 17))).isZero();
    service.sendConfirmation(UUID.randomUUID());
    verify(dispatcher, never()).dispatch(any(), any());
  }

  @Test
  void theConfirmationIsNotAReminderAndCannotBeBulkSent() {
    assertThatThrownBy(() -> service.sendReminder(ExhibitionEmailKind.CONFIRMATION))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void movingTheExpoMovesTheWholeSeries() {
    expo.setStartDate(LocalDate.of(2027, 3, 4));
    candidates(1);

    assertThat(service.sendDueReminders(LocalDate.of(2026, 10, 17))).isZero();
    service.sendDueReminders(LocalDate.of(2027, 2, 2));
    verify(dispatcher).dispatch(any(), eq(ExhibitionEmailKind.REMINDER_T30));
  }
}
