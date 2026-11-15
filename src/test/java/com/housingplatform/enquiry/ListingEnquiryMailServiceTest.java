package com.housingplatform.enquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.enquiry.domain.ListingEnquiry;
import com.housingplatform.enquiry.service.impl.ListingEnquiryMailServiceImpl;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.OrganizationContact;
import com.housingplatform.property.domain.Property;
import com.housingplatform.shared.mail.BrandedMail;
import com.housingplatform.shared.mail.BrandedMailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ListingEnquiryMailServiceTest {

  private static final String SITE = "https://ethiobuildconnect.et";

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
      BrandedMailSender sender, boolean configured, String notify) {
    when(sender.configured()).thenReturn(configured);
    ListingEnquiryMailServiceImpl s = new ListingEnquiryMailServiceImpl(sender);
    ReflectionTestUtils.setField(s, "notifyEmail", notify);
    ReflectionTestUtils.setField(s, "replyTo", "team@ethiobuildconnect.et");
    ReflectionTestUtils.setField(s, "frontendBaseUrl", SITE + "/");
    return s;
  }

  @Test
  void sendsReceiptCompanyNoticeAndTeamCopy() {
    BrandedMailSender sender = mock(BrandedMailSender.class);
    service(sender, true, "team@ethiobuildconnect.et").sendEnquiryEmails(enquiry("sales@noah.et"));

    ArgumentCaptor<BrandedMail> captor = ArgumentCaptor.forClass(BrandedMail.class);
    verify(sender, times(3)).send(captor.capture());
    BrandedMail receipt = captor.getAllValues().get(0);
    BrandedMail company = captor.getAllValues().get(1);
    BrandedMail team = captor.getAllValues().get(2);

    assertThat(receipt.getTo()).containsExactly("abebe@example.com");
    assertThat(receipt.getReplyTo()).isEqualTo("team@ethiobuildconnect.et");
    assertThat(receipt.getSubject()).contains("2BR apartment in Bole");
    assertThat(receipt.toPlainText(SITE)).contains("Noah Real Estate", "Hello Abebe Kebede");

    assertThat(company.getTo()).containsExactly("sales@noah.et");
    assertThat(company.getReplyTo()).isEqualTo("abebe@example.com");
    assertThat(company.toPlainText(SITE))
        .contains(
            "Is this still available?",
            "+251911000000",
            SITE + "/properties/11111111-1111-1111-1111-111111111111");

    assertThat(team.getTo()).containsExactly("team@ethiobuildconnect.et");
    assertThat(team.toPlainText(SITE)).contains("sales@noah.et", "/admin/enquiries");
  }

  @Test
  void skipsCompanyNoticeWithoutCompanyEmailAndFlagsItForTheTeam() {
    BrandedMailSender sender = mock(BrandedMailSender.class);
    service(sender, true, "team@ethiobuildconnect.et").sendEnquiryEmails(enquiry(null));

    ArgumentCaptor<BrandedMail> captor = ArgumentCaptor.forClass(BrandedMail.class);
    verify(sender, times(2)).send(captor.capture());
    assertThat(captor.getAllValues().get(1).toPlainText(SITE)).contains("no email on file");
  }

  @Test
  void doesNothingWhenMailIsNotConfigured() {
    BrandedMailSender sender = mock(BrandedMailSender.class);
    service(sender, false, "team@ethiobuildconnect.et").sendEnquiryEmails(enquiry("sales@noah.et"));
    verify(sender, never()).send(any(BrandedMail.class));
  }
}
