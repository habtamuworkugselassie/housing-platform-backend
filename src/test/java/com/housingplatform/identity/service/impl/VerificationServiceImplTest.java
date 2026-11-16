package com.housingplatform.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import com.housingplatform.shared.sms.SmsSender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationServiceImplTest {

  private static final String PHONE = "+251911223344";

  @Mock private UserRepository userRepository;
  @Mock private BrandedMailSender mailSender;
  @Mock private SmsSender smsSender;

  private VerificationServiceImpl service;
  private MutableClock clock;

  @BeforeEach
  void setUp() {
    service = new VerificationServiceImpl(userRepository, mailSender, smsSender);
    clock = new MutableClock(Instant.parse("2026-11-16T10:00:00Z"));
    service.setClock(clock);
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("buyer@example.com");
    user.setFirstName("Abebe");
    when(userRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
    when(mailSender.configured()).thenReturn(true);
    when(mailSender.send(any())).thenReturn(true);
  }

  private String sendAndCaptureCode() {
    service.sendOtp(PHONE, "en");
    ArgumentCaptor<BrandedMail> mail = ArgumentCaptor.forClass(BrandedMail.class);
    verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(mail.capture());
    BrandedMail sent = mail.getValue();
    assertThat(sent.getTo()).containsExactly("buyer@example.com");
    return sent.getDetails().get(0).value();
  }

  @Test
  void emailsTheCodeToTheAccountOwnerAndAcceptsItOnce() {
    String code = sendAndCaptureCode();
    assertThat(code).matches("\\d{6}");
    assertThat(service.verifyOtp(PHONE, code)).isTrue();
    assertThat(service.verifyOtp(PHONE, code)).isFalse();
  }

  @Test
  void codeExpiresAfterTtl() {
    String code = sendAndCaptureCode();
    clock.advance(VerificationServiceImpl.OTP_TTL.plusSeconds(1));
    assertThat(service.verifyOtp(PHONE, code)).isFalse();
  }

  @Test
  void codeIsBurnedAfterTooManyWrongGuesses() {
    String code = sendAndCaptureCode();
    String wrong = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < VerificationServiceImpl.MAX_ATTEMPTS; i++) {
      assertThat(service.verifyOtp(PHONE, wrong)).isFalse();
    }
    assertThat(service.verifyOtp(PHONE, code)).isFalse();
  }

  @Test
  void resendIsThrottled() {
    service.sendOtp(PHONE, "en");
    assertThatThrownBy(() -> service.sendOtp(PHONE, "en")).isInstanceOf(BusinessException.class);
    clock.advance(VerificationServiceImpl.RESEND_COOLDOWN.plusSeconds(1));
    service.sendOtp(PHONE, "en");
  }

  @Test
  void failedDeliveryIsReportedAndCanBeRetriedImmediately() {
    when(mailSender.send(any())).thenReturn(false);
    assertThatThrownBy(() -> service.sendOtp(PHONE, "en")).isInstanceOf(BusinessException.class);
    when(mailSender.send(any())).thenReturn(true);
    service.sendOtp(PHONE, "en");
  }

  @Test
  void unknownNumberDoesNotRevealThatItIsUnregistered() {
    when(userRepository.findByPhoneNumber("+251900000000")).thenReturn(Optional.empty());
    service.sendOtp("+251900000000", "en");
    verify(mailSender, never()).send(any());
  }

  @Test
  void amharicRequestGetsAmharicEmail() {
    service.sendOtp(PHONE, "am");
    ArgumentCaptor<BrandedMail> mail = ArgumentCaptor.forClass(BrandedMail.class);
    verify(mailSender).send(mail.capture());
    assertThat(mail.getValue().getLang()).isEqualTo("am");
  }

  @Test
  void smsDeliversTheCodeEvenWithoutAnEmailOnTheAccount() {
    when(smsSender.configured()).thenReturn(true);
    when(smsSender.send(any(), any())).thenReturn(true);
    when(userRepository.findByPhoneNumber("+251922000000")).thenReturn(Optional.empty());

    service.sendOtp("+251922000000", "am");

    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(smsSender).send(org.mockito.ArgumentMatchers.eq("+251922000000"), text.capture());
    assertThat(text.getValue()).matches("Ethio Build Connect code: \\d{6}\\..*");
    assertThat(text.getValue()).hasSizeLessThanOrEqualTo(160);
    String code = text.getValue().replaceAll("\\D*(\\d{6}).*", "$1");
    assertThat(service.verifyOtp("+251922000000", code)).isTrue();
  }

  @Test
  void emailStillDeliversWhenSmsFails() {
    when(smsSender.configured()).thenReturn(true);
    when(smsSender.send(any(), any())).thenReturn(false);
    service.sendOtp(PHONE, "en");
    verify(mailSender).send(any());
  }

  @Test
  void failsWhenEveryConfiguredChannelFails() {
    when(smsSender.configured()).thenReturn(true);
    when(smsSender.send(any(), any())).thenReturn(false);
    when(mailSender.send(any())).thenReturn(false);
    assertThatThrownBy(() -> service.sendOtp(PHONE, "en")).isInstanceOf(BusinessException.class);
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
