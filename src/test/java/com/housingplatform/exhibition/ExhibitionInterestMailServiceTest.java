package com.housingplatform.exhibition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.exhibition.service.impl.ExhibitionInterestMailServiceImpl;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ExhibitionInterestMailServiceTest {

  private static final String SITE = "https://ethiobuildconnect.et";

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
      BrandedMailSender sender, boolean configured, String notify) {
    when(sender.configured()).thenReturn(configured);
    ExhibitionInterestMailServiceImpl s = new ExhibitionInterestMailServiceImpl(sender);
    ReflectionTestUtils.setField(s, "notifyEmail", notify);
    ReflectionTestUtils.setField(s, "replyTo", "team@ethiobuildconnect.et");
    ReflectionTestUtils.setField(s, "frontendBaseUrl", SITE + "/");
    return s;
  }

  @Test
  void sendsReceiptToRegistrantAndNoticeToTeam() {
    BrandedMailSender sender = mock(BrandedMailSender.class);
    service(sender, true, "team@ethiobuildconnect.et, sales@ethiobuildconnect.et")
        .sendInterestEmails(interest());

    ArgumentCaptor<BrandedMail> captor = ArgumentCaptor.forClass(BrandedMail.class);
    verify(sender, times(2)).send(captor.capture());
    BrandedMail receipt = captor.getAllValues().get(0);
    BrandedMail notice = captor.getAllValues().get(1);

    assertThat(receipt.getTo()).containsExactly("buyer@example.com");
    assertThat(receipt.getReplyTo()).isEqualTo("team@ethiobuildconnect.et");
    assertThat(receipt.toPlainText(SITE))
        .contains(
            "Package: Ethio Build Connect - Platinum (1st tier)",
            "Acme Builders",
            "https://ethiobuildconnect.et");
    assertThat(notice.getTo())
        .containsExactly("team@ethiobuildconnect.et", "sales@ethiobuildconnect.et");
    assertThat(notice.getReplyTo()).isEqualTo("buyer@example.com");
    assertThat(notice.getSubject()).contains("Exhibitor", "buyer@example.com");
    assertThat(notice.toPlainText(SITE))
        .contains("/admin/exhibition-interests", "Call me in the afternoon");
  }

  @Test
  void withoutMailConfigurationSendsNothing() {
    BrandedMailSender sender = mock(BrandedMailSender.class);
    service(sender, false, "team@ethiobuildconnect.et").sendInterestEmails(interest());
    verify(sender, never()).send(any(BrandedMail.class));
  }

  @Test
  void withoutNotifyAddressOnlyTheReceiptGoesOut() {
    BrandedMailSender sender = mock(BrandedMailSender.class);
    service(sender, true, "").sendInterestEmails(interest());
    verify(sender, times(1)).send(any(BrandedMail.class));
  }
}
