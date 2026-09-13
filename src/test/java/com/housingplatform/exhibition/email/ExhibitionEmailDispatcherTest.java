package com.housingplatform.exhibition.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.exhibition.config.ExpoProperties;
import com.housingplatform.exhibition.domain.ExhibitionEmailKind;
import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.domain.ExhibitionInterestEmail;
import com.housingplatform.exhibition.repository.ExhibitionInterestEmailRepository;
import com.housingplatform.exhibition.repository.ExhibitionInterestRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The dispatcher's job is to be boring and exactly-once.
 *
 * <p>What is actually being defended here is the recipient. Mailing the same person the same
 * reminder twice because a job was rerun, or mailing someone who has opted out, is the failure that
 * costs a sender its reputation — and a new domain has none to spare.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExhibitionEmailDispatcherTest {

  @Mock private ExhibitionInterestRepository interestRepository;
  @Mock private ExhibitionInterestEmailRepository emailRepository;
  @Mock private JavaMailSender mailSender;

  private ExpoProperties expo;
  private ExhibitionEmailDispatcher dispatcher;
  private ExhibitionInterest interest;
  private final UUID interestId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    expo = new ExpoProperties();
    ExhibitionEmailComposer composer = new ExhibitionEmailComposer(expo);
    ReflectionTestUtils.setField(composer, "frontendBaseUrl", "https://ethiobuildconnect.et");

    dispatcher =
        new ExhibitionEmailDispatcher(
            interestRepository, emailRepository, composer, mailSender, expo);
    ReflectionTestUtils.setField(dispatcher, "fromEmail", "expo@ethiobuildconnect.et");

    interest =
        ExhibitionInterest.builder()
            .email("lead@example.com")
            .interestType("visitor")
            .unsubscribeToken("tok-123")
            .build();
    interest.setId(interestId);

    when(interestRepository.findById(interestId)).thenReturn(Optional.of(interest));
    when(emailRepository.findByInterestIdAndKind(any(), any())).thenReturn(Optional.empty());
    when(emailRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private ExhibitionInterestEmail savedRecord() {
    ArgumentCaptor<ExhibitionInterestEmail> captor =
        ArgumentCaptor.forClass(ExhibitionInterestEmail.class);
    verify(emailRepository).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  @Test
  void sendsTheMailAndLogsIt() {
    boolean sent = dispatcher.dispatch(interestId, ExhibitionEmailKind.CONFIRMATION);

    assertThat(sent).isTrue();
    ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(mail.capture());
    assertThat(mail.getValue().getTo()).containsExactly("lead@example.com");
    assertThat(mail.getValue().getSubject()).isNotBlank();

    ExhibitionInterestEmail record = savedRecord();
    assertThat(record.getStatus()).isEqualTo(ExhibitionInterestEmail.Status.SENT);
    assertThat(record.getSentAt()).isNotNull();
    assertThat(record.getAttempts()).isEqualTo(1);
    assertThat(record.getRecipient()).isEqualTo("lead@example.com");
  }

  @Test
  void neverSendsTheSameMailTwice() {
    when(emailRepository.findByInterestIdAndKind(interestId, ExhibitionEmailKind.REMINDER_T7))
        .thenReturn(
            Optional.of(
                ExhibitionInterestEmail.builder()
                    .interest(interest)
                    .kind(ExhibitionEmailKind.REMINDER_T7)
                    .recipient("lead@example.com")
                    .status(ExhibitionInterestEmail.Status.SENT)
                    .attempts(1)
                    .sentAt(LocalDateTime.now())
                    .build()));

    boolean sent = dispatcher.dispatch(interestId, ExhibitionEmailKind.REMINDER_T7);

    assertThat(sent).isFalse();
    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void aFailedAttemptStaysRetryable() {
    when(emailRepository.findByInterestIdAndKind(interestId, ExhibitionEmailKind.REMINDER_T7))
        .thenReturn(
            Optional.of(
                ExhibitionInterestEmail.builder()
                    .interest(interest)
                    .kind(ExhibitionEmailKind.REMINDER_T7)
                    .recipient("lead@example.com")
                    .status(ExhibitionInterestEmail.Status.FAILED)
                    .attempts(1)
                    .detail("connection reset")
                    .build()));

    boolean sent = dispatcher.dispatch(interestId, ExhibitionEmailKind.REMINDER_T7);

    assertThat(sent).isTrue();
    verify(mailSender).send(any(SimpleMailMessage.class));
    assertThat(savedRecord().getAttempts()).isEqualTo(2);
  }

  @Test
  void aFailureIsRecordedRatherThanThrown() {
    doThrow(new MailSendException("smtp refused"))
        .when(mailSender)
        .send(any(SimpleMailMessage.class));

    boolean sent = dispatcher.dispatch(interestId, ExhibitionEmailKind.CONFIRMATION);

    assertThat(sent).isFalse();
    ExhibitionInterestEmail record = savedRecord();
    assertThat(record.getStatus()).isEqualTo(ExhibitionInterestEmail.Status.FAILED);
    assertThat(record.getDetail()).contains("smtp refused");
    assertThat(record.getSentAt()).isNull();
  }

  @Test
  void anOptedOutRegistrantGetsNoReminder() {
    interest.setUnsubscribedAt(LocalDateTime.now());

    boolean sent = dispatcher.dispatch(interestId, ExhibitionEmailKind.REMINDER_T30);

    assertThat(sent).isFalse();
    verify(mailSender, never()).send(any(SimpleMailMessage.class));
    ExhibitionInterestEmail record = savedRecord();
    assertThat(record.getStatus()).isEqualTo(ExhibitionInterestEmail.Status.SUPPRESSED);
    assertThat(record.getDetail()).isEqualTo("registrant unsubscribed");
  }

  @Test
  void anOptedOutRegistrantStillGetsTheirConfirmation() {
    // Opting out of reminders is not opting out of being answered. Someone who registers and
    // immediately unsubscribes has still asked us a question, and the confirmation is the reply.
    interest.setUnsubscribedAt(LocalDateTime.now());

    boolean sent = dispatcher.dispatch(interestId, ExhibitionEmailKind.CONFIRMATION);

    assertThat(sent).isTrue();
    verify(mailSender).send(any(SimpleMailMessage.class));
  }

  @Test
  void withNoSmtpAccountNothingIsSentAndTheReasonIsRecorded() {
    ReflectionTestUtils.setField(dispatcher, "fromEmail", "");

    boolean sent = dispatcher.dispatch(interestId, ExhibitionEmailKind.CONFIRMATION);

    assertThat(sent).isFalse();
    verify(mailSender, never()).send(any(SimpleMailMessage.class));
    assertThat(savedRecord().getStatus()).isEqualTo(ExhibitionInterestEmail.Status.SUPPRESSED);
  }

  @Test
  void aDeletedRegistrantIsSkippedQuietly() {
    when(interestRepository.findById(interestId)).thenReturn(Optional.empty());

    assertThat(dispatcher.dispatch(interestId, ExhibitionEmailKind.CONFIRMATION)).isFalse();
    verify(mailSender, never()).send(any(SimpleMailMessage.class));
    verify(emailRepository, never()).saveAndFlush(any());
  }

  @Test
  void theReplyToAddressIsSetWhenOneIsConfigured() {
    expo.getLifecycleEmails().setReplyTo("expo-team@ethiobuildconnect.et");

    dispatcher.dispatch(interestId, ExhibitionEmailKind.CONFIRMATION);

    ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(mail.capture());
    assertThat(mail.getValue().getReplyTo()).isEqualTo("expo-team@ethiobuildconnect.et");
  }
}
