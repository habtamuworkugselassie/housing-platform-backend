package com.housingplatform.enquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.housingplatform.enquiry.domain.ListingEnquiry;
import com.housingplatform.enquiry.service.impl.ListingEnquiryMailServiceImpl;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationContact;
import com.housingplatform.property.domain.Property;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class ListingEnquiryMailServiceTest {

  private static ListingEnquiry enquiry(String companyEmail) {
    Organization org = Organization.builder().name("Noah Real Estate").build();
    if (companyEmail != null) {
      org.setContact(OrganizationContact.builder().email(companyEmail).build());
    }
    Property property = new Property();
    property.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    property.setTitle("2BR apartment in Bole");
    return ListingEnquiry.builder()
        .subjectType(ListingEnquiry.SubjectType.PROPERTY)
        .subjectTitle("2BR apartment in Bole")
        .property(property)
        .organization(org)
        .name("Abebe Kebede")
        .email("abebe@example.com")
        .phoneNumber("+251911000000")
        .message("Is this still available? I can visit on Saturday.")
        .build();
  }

  private static ListingEnquiryMailServiceImpl service(
      JavaMailSender sender, String from, String notify) {
    ListingEnquiryMailServiceImpl s = new ListingEnquiryMailServiceImpl(sender);
    ReflectionTestUtils.setField(s, "fromEmail", from);
    ReflectionTestUtils.setField(s, "notifyEmail", notify);
    ReflectionTestUtils.setField(s, "replyTo", "team@ethiobuildconnect.et");
    ReflectionTestUtils.setField(s, "frontendBaseUrl", "https://ethiobuildconnect.et/");
    return s;
  }

  @Test
  void sendsReceiptCompanyNoticeAndTeamCopy() {
    JavaMailSender sender = mock(JavaMailSender.class);
    service(sender, "noreply@ethiobuildconnect.et", "team@ethiobuildconnect.et")
        .sendEnquiryEmails(enquiry("sales@noah.et"));

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender, times(3)).send(captor.capture());
    SimpleMailMessage receipt = captor.getAllValues().get(0);
    SimpleMailMessage company = captor.getAllValues().get(1);
    SimpleMailMessage team = captor.getAllValues().get(2);

    assertThat(receipt.getTo()).containsExactly("abebe@example.com");
    assertThat(receipt.getReplyTo()).isEqualTo("team@ethiobuildconnect.et");
    assertThat(receipt.getSubject()).contains("2BR apartment in Bole");
    assertThat(receipt.getText()).contains("Noah Real Estate", "Hello Abebe Kebede");

    assertThat(company.getTo()).containsExactly("sales@noah.et");
    assertThat(company.getReplyTo()).isEqualTo("abebe@example.com");
    assertThat(company.getText())
        .contains(
            "Is this still available?",
            "+251911000000",
            "https://ethiobuildconnect.et/properties/11111111-1111-1111-1111-111111111111");

    assertThat(team.getTo()).containsExactly("team@ethiobuildconnect.et");
    assertThat(team.getText()).contains("sales@noah.et", "/admin/enquiries");
  }

  @Test
  void skipsCompanyNoticeWithoutCompanyEmailAndFlagsItForTheTeam() {
    JavaMailSender sender = mock(JavaMailSender.class);
    service(sender, "noreply@ethiobuildconnect.et", "team@ethiobuildconnect.et")
        .sendEnquiryEmails(enquiry(null));

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender, times(2)).send(captor.capture());
    assertThat(captor.getAllValues().get(1).getText()).contains("no email on file");
  }

  @Test
  void doesNothingWhenMailIsNotConfigured() {
    JavaMailSender sender = mock(JavaMailSender.class);
    service(sender, "", "team@ethiobuildconnect.et").sendEnquiryEmails(enquiry("sales@noah.et"));
    verify(sender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void aFailedReceiptDoesNotStopTheCompanyNotice() {
    JavaMailSender sender = mock(JavaMailSender.class);
    doThrow(new RuntimeException("smtp down"))
        .doNothing()
        .when(sender)
        .send(any(SimpleMailMessage.class));
    service(sender, "noreply@ethiobuildconnect.et", "").sendEnquiryEmails(enquiry("sales@noah.et"));
    verify(sender, times(2)).send(any(SimpleMailMessage.class));
  }
}
