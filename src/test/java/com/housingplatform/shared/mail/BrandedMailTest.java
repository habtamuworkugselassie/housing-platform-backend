package com.housingplatform.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class BrandedMailTest {

  private static final String SITE = "https://ethiobuildconnect.et";
  private static final String LOGO = SITE + "/images/branding/ethio-build-connect-wordmark.png";

  private static BrandedMail sample() {
    return BrandedMail.create()
        .to("abebe@example.com")
        .replyTo("team@ethiobuildconnect.et")
        .subject("We received your enquiry - 2BR <Bole>")
        .preheader("Noah has your message.")
        .title("We received your enquiry")
        .greeting("Hello Abebe,")
        .paragraph("Thank you & welcome.")
        .detail("Page", SITE + "/properties/1")
        .detail("Email", "abebe@example.com")
        .detail("Phone", null)
        .message("Your message", "Line one\nLine two <b>")
        .cta("View the listing", SITE + "/properties/1")
        .closing("Reply to this email for help.");
  }

  @Test
  void htmlHasLogoHeaderEscapedContentDetailsMessageAndButton() {
    String html = sample().toHtml(SITE, LOGO);
    assertThat(html)
        .contains("<img src=\"" + LOGO + "\"")
        .contains("background-image:linear-gradient(#2e1065,#2e1065)")
        .contains("We received your enquiry - 2BR &lt;Bole&gt;")
        .contains("Thank you &amp; welcome.")
        .contains("<a href=\"" + SITE + "/properties/1\" style=\"color:#6d28d9;\">")
        .contains("mailto:abebe@example.com")
        .contains("Line one\nLine two &lt;b&gt;")
        .contains("background-image:linear-gradient(#f4c977,#f4c977)")
        .contains(">View the listing</a>")
        .doesNotContain("Phone");
  }

  @Test
  void plainTextCarriesEverythingWithoutMarkup() {
    String text = sample().toPlainText(SITE);
    assertThat(text)
        .contains("Hello Abebe,", "Thank you & welcome.", "Page: " + SITE + "/properties/1")
        .contains("Your message:\nLine one\nLine two <b>")
        .contains("View the listing: " + SITE + "/properties/1")
        .contains("Ethio Build Connect\n" + SITE)
        .doesNotContain("<p");
  }

  @Test
  void senderBuildsAMultipartMessageWithNamedFromAndReplyTo() throws Exception {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    BrandedMailSenderImpl sender = new BrandedMailSenderImpl(mailSender);
    ReflectionTestUtils.setField(sender, "fromEmail", "noreply@ethiobuildconnect.et");
    ReflectionTestUtils.setField(sender, "fromName", "Ethio Build Connect");
    ReflectionTestUtils.setField(sender, "frontendBaseUrl", SITE + "/");
    ReflectionTestUtils.setField(sender, "logoUrl", "");

    assertThat(sender.send(sample())).isTrue();

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    MimeMessage m = captor.getValue();
    assertThat(m.getFrom()[0].toString())
        .isEqualTo("Ethio Build Connect <noreply@ethiobuildconnect.et>");
    assertThat(m.getReplyTo()[0].toString()).isEqualTo("team@ethiobuildconnect.et");
    assertThat(m.getSubject()).isEqualTo("We received your enquiry - 2BR <Bole>");
    assertThat(m.getContent()).isInstanceOf(MimeMultipart.class);
  }

  @Test
  void senderReportsFalseWhenNotConfigured() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    BrandedMailSenderImpl sender = new BrandedMailSenderImpl(mailSender);
    ReflectionTestUtils.setField(sender, "fromEmail", "");
    assertThat(sender.configured()).isFalse();
    assertThat(sender.send(sample())).isFalse();
    verify(mailSender, org.mockito.Mockito.never()).send(any(MimeMessage.class));
  }
}
