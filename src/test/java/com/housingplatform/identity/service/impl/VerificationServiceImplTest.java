package com.housingplatform.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.VerificationService.OtpVerification;
import com.housingplatform.identity.service.otp.InMemoryOtpStore;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import com.housingplatform.shared.sms.SmsSender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
  private User user;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-11-16T10:00:00Z"));
    service =
        new VerificationServiceImpl(
            userRepository, mailSender, smsSender, new InMemoryOtpStore(clock));
    service.setClock(clock);
    user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("buyer@example.com");
    user.setFirstName("Abebe");
    when(userRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
    when(mailSender.configured()).thenReturn(true);
    when(mailSender.send(any())).thenReturn(true);
  }

  private void enableSms(boolean delivers) {
    when(smsSender.configured()).thenReturn(true);
    when(smsSender.send(any(), any())).thenReturn(delivers);
  }

  private String emailedCode() {
    ArgumentCaptor<BrandedMail> mail = ArgumentCaptor.forClass(BrandedMail.class);
    verify(mailSender, atLeastOnce()).send(mail.capture());
    assertThat(mail.getValue().getTo()).containsExactly("buyer@example.com");
    return mail.getValue().getDetails().get(0).value();
  }

  private String textedCode() {
    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(smsSender, atLeastOnce()).send(eq(PHONE), text.capture());
    return text.getValue().replaceAll("\\D*(\\d{6}).*", "$1");
  }

  private void nextMinute() {
    clock.advance(VerificationServiceImpl.RESEND_COOLDOWN.plusSeconds(1));
  }

  @Test
  void emailedCodeIsSingleUseAndProvesOnlyTheEmail() {
    service.sendOtp(PHONE, "en");
    String code = emailedCode();
    assertThat(code).matches("\\d{6}");

    OtpVerification result = service.verify(PHONE, code);
    assertThat(result).isEqualTo(new OtpVerification(true, false, true));
    assertThat(service.verify(PHONE, code).valid()).isFalse();
  }

  @Test
  void textedCodeProvesThePhone() {
    enableSms(true);
    service.sendOtp(PHONE, "en");
    OtpVerification result = service.verify(PHONE, textedCode());
    assertThat(result.valid()).isTrue();
    assertThat(result.phoneProven()).isTrue();
  }

  @Test
  void codeExpiresAfterTtl() {
    service.sendOtp(PHONE, "en");
    String code = emailedCode();
    clock.advance(VerificationServiceImpl.OTP_TTL.plusSeconds(1));
    assertThat(service.verify(PHONE, code).valid()).isFalse();
  }

  @Test
  void codeIsBurnedAfterTooManyWrongGuesses() {
    service.sendOtp(PHONE, "en");
    String code = emailedCode();
    String wrong = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < VerificationServiceImpl.MAX_ATTEMPTS; i++) {
      assertThat(service.verify(PHONE, wrong).valid()).isFalse();
    }
    assertThat(service.verify(PHONE, code).valid()).isFalse();
  }

  @Test
  void resendIsThrottled() {
    service.sendOtp(PHONE, "en");
    assertThatThrownBy(() -> service.sendOtp(PHONE, "en")).isInstanceOf(BusinessException.class);
    nextMinute();
    service.sendOtp(PHONE, "en");
  }

  @Test
  void dailyCapPerPhone() {
    service.setLimits(2, 60, "251");
    service.sendOtp(PHONE, "en");
    nextMinute();
    service.sendOtp(PHONE, "en");
    nextMinute();
    assertThatThrownBy(() -> service.sendOtp(PHONE, "en"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("today");
  }

  @Test
  void unregisteredNumbersGetNothingButTheSameAnswer() {
    enableSms(true);
    when(userRepository.findByPhoneNumber("+251900000000")).thenReturn(Optional.empty());
    service.sendOtp("+251900000000", "en");
    verify(smsSender, never()).send(any(), any());
    verify(mailSender, never()).send(any());
  }

  @Test
  void smsOnlyGoesToAllowedCountries() {
    enableSms(true);
    when(userRepository.findByPhoneNumber("+447700900123")).thenReturn(Optional.of(user));
    service.sendOtp("+447700900123", "en");
    verify(smsSender, never()).send(any(), any());
    verify(mailSender).send(any());
  }

  @Test
  void hourlySmsCeilingFallsBackToEmail() {
    enableSms(true);
    service.setLimits(8, 1, "251");
    service.sendOtp(PHONE, "en");
    nextMinute();
    service.sendOtp(PHONE, "en");
    verify(smsSender, times(1)).send(any(), any());
    verify(mailSender, times(2)).send(any());
  }

  @Test
  void smsAloneIsEnoughWhenTheAccountHasNoEmail() {
    enableSms(true);
    user.setEmail(null);
    service.sendOtp(PHONE, "am");
    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(smsSender).send(eq(PHONE), text.capture());
    assertThat(text.getValue()).startsWith("Ethio Build Connect code: ").hasSizeLessThan(160);
    verify(mailSender, never()).send(any());
  }

  @Test
  void emailStillDeliversWhenSmsFails() {
    enableSms(false);
    service.sendOtp(PHONE, "en");
    assertThat(service.verify(PHONE, emailedCode()).valid()).isTrue();
  }

  @Test
  void failsWhenEveryConfiguredChannelFails() {
    enableSms(false);
    when(mailSender.send(any())).thenReturn(false);
    assertThatThrownBy(() -> service.sendOtp(PHONE, "en")).isInstanceOf(BusinessException.class);
  }

  @Test
  void amharicRequestGetsAmharicEmail() {
    service.sendOtp(PHONE, "am");
    ArgumentCaptor<BrandedMail> mail = ArgumentCaptor.forClass(BrandedMail.class);
    verify(mailSender).send(mail.capture());
    assertThat(mail.getValue().getLang()).isEqualTo("am");
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
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
