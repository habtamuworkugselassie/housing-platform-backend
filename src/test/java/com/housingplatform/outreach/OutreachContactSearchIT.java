package com.housingplatform.outreach;

import static org.assertj.core.api.Assertions.assertThat;

import com.housingplatform.BaseIntegrationTest;
import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.repository.OutreachContactRepository;
import com.housingplatform.outreach.service.OutreachService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/** The admin list with and without filters, through the real repository (dynamic criteria). */
class OutreachContactSearchIT extends BaseIntegrationTest {

  @Autowired OutreachContactRepository contacts;
  @Autowired OutreachService service;

  @Test
  void listsWithNoFiltersAndWithEachFilter() {
    contacts.save(
        OutreachContact.builder()
            .name("Noah Real Estate")
            .sector("Real estate developer")
            .email("info@noah.et")
            .build());
    contacts.save(
        OutreachContact.builder()
            .name("Awash Bank")
            .sector("Bank")
            .email("info@awash.et")
            .status(OutreachContact.Status.SENT)
            .build());

    assertThat(service.search(null, null, null, PageRequest.of(0, 50)).getTotalElements())
        .isEqualTo(2);
    assertThat(service.search(null, "", "", PageRequest.of(0, 50)).getTotalElements()).isEqualTo(2);
    assertThat(
            service
                .search(OutreachContact.Status.SENT, null, null, PageRequest.of(0, 50))
                .getContent())
        .extracting(r -> r.getName())
        .containsExactly("Awash Bank");
    assertThat(service.search(null, "bank", null, PageRequest.of(0, 50)).getTotalElements())
        .isEqualTo(1);
    assertThat(service.search(null, null, "NOAH", PageRequest.of(0, 50)).getContent())
        .extracting(r -> r.getEmail())
        .containsExactly("info@noah.et");
  }
}
