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
            "Noah at the expo",
            "Dear Noah team,\n\nI saw your Atlas launch & liked it.\n\n- Exhibit: a stand\n- List now: free\n\nSee https://ethiobuildconnect.et/#sponsorship\n\nKind regards,\nHabtamu\n\nIf you would rather not hear from us, reply no thanks.",
            "en",
            true,
            "https://ethiobuildconnect.et/?interest=exhibitor",
            "https://ethiobuildconnect.et",
            "https://x/logo.png");
    assertThat(html)
        .contains(">Noah at the expo</h1>")
        .contains(">Dear Noah team,</p>")
        .contains("Atlas launch &amp; liked it")
        .contains("&#9670;</td>")
        .contains("Exhibit: a stand")
        .contains("<a href=\"https://ethiobuildconnect.et/#sponsorship\"")
        .contains("9–13 January 2027")
        .contains("Addis International Convention Center")
        .contains("Sponsorship packages")
        .contains("ETB 5,000,000")
        .contains("54 m² booth")
        .contains("Special Participant Package")
        .contains(">Register on the portal</a>")
        .contains("Kind regards,\nHabtamu")
        .contains(
            "<span style=\"color:#8b7fa6;\">If you would rather not hear from us, reply no thanks.</span>");
    String am =
        OutreachHtml.render(
            "ርዕስ",
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
        .containsExactly("brochure-am.pdf", "sponsorship-am.pdf", "agreement-am.pdf");
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
    assertThat(m.getRecipients(jakarta.mail.Message.RecipientType.BCC)[0].toString())
        .isEqualTo("ethiobuildconnectexpo@gmail.com");
    assertThat(m.getHeader("In-Reply-To")[0]).isEqualTo("<first@ethiobuildconnect.et>");
    assertThat(m.getHeader("Message-ID")[0]).isEqualTo(id);
    MimeMultipart mp = (MimeMultipart) m.getContent();
    int parts = mp.getCount();
    assertThat(parts).isEqualTo(4); // body (text+html alternative) + 3 PDFs
    assertThat(mp.getBodyPart(1).getFileName()).contains("Brochure (English)");
    assertThat(mp.getBodyPart(2).getFileName()).contains("Sponsorship packages (English)");
    assertThat(mp.getBodyPart(3).getFileName()).contains("Exhibitor agreement (English)");
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

  @Test
  void testSendsCarryNoBlindCopy() throws Exception {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(sender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    OutreachProperties props = new OutreachProperties();
    props.setSenderEmail("partnerships@ethiobuildconnect.et");
    props.setReplyTo("ethiobuildconnectexpo@gmail.com");
    OutreachMailer mailer = new OutreachMailer(sender, props);
    ReflectionTestUtils.setField(mailer, "logoUrl", "");
    mailer.send(
        "me@example.com",
        "Hello",
        "Body",
        "en",
        "https://x/",
        null,
        OutreachMailer.Options.followUp(),
        false);
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captor.capture());
    assertThat(captor.getValue().getRecipients(jakarta.mail.Message.RecipientType.BCC)).isNull();
  }
}
