package com.housingplatform.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.outreach.service.OutreachHtml;
import com.housingplatform.outreach.service.OutreachMailer;
import com.housingplatform.outreach.service.OutreachPackages;
import com.housingplatform.outreach.service.OutreachProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class OutreachMailerTest {

  @Test
  void htmlCarriesTheTextThePackagesAndTheRegisterButton() {
    String html =
        OutreachHtml.render(
            "Dear Noah team,\n\nI saw your Atlas launch & liked it.\n\nSee https://ethiobuildconnect.et/#sponsorship",
            "en",
            true,
            "https://ethiobuildconnect.et/?interest=exhibitor",
            "https://ethiobuildconnect.et",
            "https://x/logo.png");
    assertThat(html)
        .contains("<p style=\"margin:0 0 14px;white-space:pre-line;\">Dear Noah team,</p>")
        .contains("Atlas launch &amp; liked it")
        .contains("<a href=\"https://ethiobuildconnect.et/#sponsorship\"")
        .contains("Sponsorship packages")
        .contains("ETB 5,000,000")
        .contains("54 m² booth")
        .contains("Special Participant Package")
        .contains(">Register on the portal</a>");
    String am =
        OutreachHtml.render(
            "ሰላም",
            "am",
            true,
            "https://ethiobuildconnect.et/am/?interest=partner",
            "https://ethiobuildconnect.et",
            "https://x/logo.png");
    assertThat(am)
        .contains("<html lang=\"am\"")
        .contains("የስፖንሰርሺፕ ፓኬጆች")
        .contains("5,000,000 ብር")
        .contains(">በድረ-ገጹ ላይ ይመዝገቡ</a>");
    assertThat(OutreachPackages.plainText("en"))
        .contains("- Tier 2 · Gold: ETB 900,000, 18 m² booth.");
  }

  @Test
  void attachmentsFollowTheLanguageAndOptions() {
    assertThat(OutreachMailer.attachmentNames("am", OutreachMailer.Options.firstEmail()))
        .containsExactly("brochure-am.pdf", "sponsorship-am.pdf");
    assertThat(
            OutreachMailer.attachmentNames(
                "en", new OutreachMailer.Options(true, true, true, true)))
        .containsExactly("brochure-en.pdf", "sponsorship-en.pdf", "agreement-en.pdf");
    assertThat(OutreachMailer.attachmentNames("en", OutreachMailer.Options.followUp())).isEmpty();
    assertThat(OutreachMailer.displayName("sponsorship-am.pdf"))
        .isEqualTo("Ethio Build Connect Expo - Sponsorship packages (Amharic).pdf");
  }

  @Test
  void sendsAMultipartMessageWithAttachmentsFromTheSenderWithReplyToAndThreading()
      throws Exception {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(sender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    OutreachProperties props = new OutreachProperties();
    props.setSenderEmail("partnerships@ethiobuildconnect.et");
    props.setSenderName("Ethio Build Connect");
    props.setReplyTo("ethiobuildconnectexpo@gmail.com");
    OutreachMailer mailer = new OutreachMailer(sender, props);
    ReflectionTestUtils.setField(mailer, "defaultFrom", "noreply@ethiobuildconnect.et");
    ReflectionTestUtils.setField(mailer, "logoUrl", "");

    String id =
        mailer.send(
            "info@noah.et",
            "Hello",
            "Body text",
            "en",
            "https://ethiobuildconnect.et/?interest=exhibitor",
            "<first@ethiobuildconnect.et>",
            OutreachMailer.Options.firstEmail());

    assertThat(id).startsWith("<outreach-").endsWith("@ethiobuildconnect.et>");
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captor.capture());
    MimeMessage m = captor.getValue();
    assertThat(m.getFrom()[0].toString())
        .isEqualTo("Ethio Build Connect <partnerships@ethiobuildconnect.et>");
    assertThat(m.getReplyTo()[0].toString()).isEqualTo("ethiobuildconnectexpo@gmail.com");
    assertThat(m.getHeader("In-Reply-To")[0]).isEqualTo("<first@ethiobuildconnect.et>");
    assertThat(m.getHeader("Message-ID")[0]).isEqualTo(id);
    MimeMultipart mp = (MimeMultipart) m.getContent();
    int parts = mp.getCount();
    assertThat(parts).isEqualTo(3); // body (text+html alternative) + 2 PDFs
    assertThat(mp.getBodyPart(1).getFileName()).contains("Brochure (English)");
    assertThat(mp.getBodyPart(2).getFileName()).contains("Sponsorship packages (English)");
  }

  @Test
  void unconfiguredWhenNoSenderAtAll() {
    OutreachMailer mailer =
        new OutreachMailer(mock(JavaMailSender.class), new OutreachProperties());
    ReflectionTestUtils.setField(mailer, "defaultFrom", "");
    assertThat(mailer.configured()).isFalse();
    verifyNoSend();
  }

  private static void verifyNoSend() {
    JavaMailSender s = mock(JavaMailSender.class);
    verify(s, org.mockito.Mockito.never()).send(any(MimeMessage.class));
  }
}
