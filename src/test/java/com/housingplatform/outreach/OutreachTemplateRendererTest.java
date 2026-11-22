package com.housingplatform.outreach;

import static org.assertj.core.api.Assertions.assertThat;

import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.domain.OutreachTemplate;
import com.housingplatform.outreach.domain.TemplateKey;
import com.housingplatform.outreach.service.OutreachProperties;
import com.housingplatform.outreach.service.OutreachTemplateRenderer;
import org.junit.jupiter.api.Test;

class OutreachTemplateRendererTest {

  private static OutreachProperties props() {
    OutreachProperties p = new OutreachProperties();
    p.setSenderName("Habtamu Worku");
    p.setSenderTitle("Founder");
    p.setSenderPhone("+251 913 504 097");
    p.setSenderEmail("ethiobuildconnectexpo@gmail.com");
    p.setSiteUrl("https://ethiobuildconnect.et/");
    return p;
  }

  private static OutreachTemplate template() {
    return OutreachTemplate.builder()
        .templateKey("REAL_ESTATE")
        .locale("en")
        .subject("{{project_or_company}} at the expo")
        .body(
            "Dear {{contact_name}},\n\n{{hook}}\n\nRegister: {{register_url}} or see {{sponsorship_url}}")
        .build();
  }

  @Test
  void fillsPlaceholdersAndAppendsSignatureAndOptOut() {
    OutreachContact c =
        OutreachContact.builder()
            .name("Noah Real Estate")
            .sector("Real estate developer")
            .email("info@noah.et")
            .hook("I saw your Bole Atlas launch last week.")
            .locale("en")
            .build();
    OutreachTemplateRenderer.Rendered r =
        new OutreachTemplateRenderer(props()).render(template(), c, null);
    assertThat(r.subject()).isEqualTo("Noah Real Estate at the expo");
    assertThat(r.body())
        .contains("Dear Noah Real Estate team,")
        .contains("I saw your Bole Atlas launch last week.")
        .contains("https://ethiobuildconnect.et/?interest=exhibitor")
        .contains("https://ethiobuildconnect.et/reserve")
        .contains("Kind regards,\nHabtamu Worku\nFounder, Ethio Build Connect")
        .contains("+251 913 504 097 · ethiobuildconnectexpo@gmail.com")
        .endsWith("reply \"no thanks\" and we will not write again.");
  }

  @Test
  void amharicContactsGetAmharicLinksAndClosing() {
    OutreachContact c =
        OutreachContact.builder()
            .name("Awash Bank")
            .sector("Bank")
            .email("info@awash.et")
            .locale("am")
            .contactName("አቶ ተስፋዬ")
            .build();
    OutreachTemplate t = template();
    t.setBody("ውድ {{contact_name}}፣\n{{hook}}\n{{register_url}}");
    OutreachTemplateRenderer.Rendered r = new OutreachTemplateRenderer(props()).render(t, c, null);
    assertThat(r.body())
        .contains("ውድ አቶ ተስፋዬ፣")
        .contains("https://ethiobuildconnect.et/am/?interest=partner")
        .contains("የAwash Bankን ሥራ አይቼ")
        .contains("ከአክብሮት ጋር፣")
        .contains("አልፈልግም");
  }

  @Test
  void sectorMapsToTemplateAndRegistrationInterest() {
    assertThat(TemplateKey.forSector("Mortgage bank")).isEqualTo(TemplateKey.BANK);
    assertThat(TemplateKey.forSector("Reinsurance")).isEqualTo(TemplateKey.INSURANCE);
    assertThat(TemplateKey.forSector("Cement")).isEqualTo(TemplateKey.CONTRACTOR);
    assertThat(TemplateKey.forSector("Architecture & engineering consultancy"))
        .isEqualTo(TemplateKey.CONSULTANCY);
    assertThat(TemplateKey.forSector("Events & venues")).isEqualTo(TemplateKey.ASSOCIATION);
    assertThat(TemplateKey.forSector("Government").interest()).isEqualTo("partner");
    assertThat(TemplateKey.forSector("Real estate agency").interest()).isEqualTo("exhibitor");
    OutreachContact c =
        OutreachContact.builder()
            .sector("Media")
            .templateKey("BANK")
            .email("x@y.z")
            .name("X")
            .build();
    assertThat(OutreachTemplateRenderer.effectiveKey(c)).isEqualTo(TemplateKey.BANK);
  }

  @Test
  void signatureNamesTheBrandOnceWhenTheSenderIsTheBrand() {
    OutreachProperties props = new OutreachProperties();
    props.setSenderName("Ethio Build Connect");
    OutreachTemplateRenderer r = new OutreachTemplateRenderer(props);
    OutreachTemplate t = new OutreachTemplate();
    t.setSubject("Hi");
    t.setBody("Hello {{name}}");
    OutreachContact c = new OutreachContact();
    c.setName("Noah");
    c.setLocale("en");
    String body = r.render(t, c, null).body();
    assertThat(body.split("Ethio Build Connect", -1).length - 1).isEqualTo(1);
    assertThat(body).contains("Kind regards,\nEthio Build Connect\nDream Team Trading PLC");

    props.setSenderName("Habtamu Worku");
    props.setSenderTitle("Partnerships");
    body = r.render(t, c, null).body();
    assertThat(body).contains("Habtamu Worku\nPartnerships, Ethio Build Connect\n");
  }
}
