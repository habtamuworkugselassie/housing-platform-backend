package com.housingplatform.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.service.impl.ExhibitionInterestMailServiceImpl;
import com.housingplatform.identity.domain.Sponsorship;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class ExhibitionInterestMailServiceTest {

  private static ExhibitionInterest interest() {
    Sponsorship pkg = new Sponsorship();
    pkg.setName("Ethio Build Connect - Platinum (1st tier)");
    return ExhibitionInterest.builder()
        .email("buyer@example.com")
        .phoneNumber("+251911000000")
        .interestType("exhibitor")
        .company("Acme Builders")
        .message("Call me in the afternoon")
        .sponsorship(pkg)
        .build();
  }

  private static ExhibitionInterestMailServiceImpl service(
      JavaMailSender sender, String from, String notify) {
    ExhibitionInterestMailServiceImpl s = new ExhibitionInterestMailServiceImpl(sender);
    ReflectionTestUtils.setField(s, "fromEmail", from);
    ReflectionTestUtils.setField(s, "notifyEmail", notify);
    ReflectionTestUtils.setField(s, "frontendBaseUrl", "https://ethiobuildconnect.et/");
    return s;
  }

  @Test
  void sendsReceiptToRegistrantAndNoticeToTeam() {
    JavaMailSender sender = mock(JavaMailSender.class);
    service(
            sender,
            "noreply@ethiobuildconnect.et",
            "team@ethiobuildconnect.et, sales@ethiobuildconnect.et")
        .sendInterestEmails(interest());

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender, times(2)).send(captor.capture());
    SimpleMailMessage receipt = captor.getAllValues().get(0);
    SimpleMailMessage notice = captor.getAllValues().get(1);

    assertThat(receipt.getTo()).containsExactly("buyer@example.com");
    assertThat(receipt.getText())
        .contains(
            "Package: Ethio Build Connect - Platinum (1st tier)",
            "Acme Builders",
            "https://ethiobuildconnect.et");
    assertThat(notice.getTo())
        .containsExactly("team@ethiobuildconnect.et", "sales@ethiobuildconnect.et");
    assertThat(notice.getReplyTo()).isEqualTo("buyer@example.com");
    assertThat(notice.getSubject()).contains("Exhibitor", "buyer@example.com");
    assertThat(notice.getText())
        .contains("/admin/exhibition-interests", "Call me in the afternoon");
  }

  @Test
  void withoutMailConfigurationSendsNothing() {
    JavaMailSender sender = mock(JavaMailSender.class);
    service(sender, "", "team@ethiobuildconnect.et").sendInterestEmails(interest());
    verify(sender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void aFailingMailServerIsLoggedNotThrown() {
    JavaMailSender sender = mock(JavaMailSender.class);
    doThrow(new RuntimeException("smtp down")).when(sender).send(any(SimpleMailMessage.class));
    service(sender, "noreply@ethiobuildconnect.et", "").sendInterestEmails(interest());
    verify(sender, times(1)).send(any(SimpleMailMessage.class));
  }
}
