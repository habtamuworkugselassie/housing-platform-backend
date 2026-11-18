package com.housingplatform.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.dto.OutreachDtos.ImportResult;
import com.housingplatform.outreach.repository.OutreachContactRepository;
import com.housingplatform.outreach.repository.OutreachMessageRepository;
import com.housingplatform.outreach.repository.OutreachTemplateRepository;
import com.housingplatform.outreach.service.GmailClient;
import com.housingplatform.outreach.service.OutreachService;
import com.housingplatform.outreach.service.OutreachTemplateRenderer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutreachServiceTest {

  @Test
  void importsQuotedCsvSkipsInvalidAndProtectsOptedOut() throws Exception {
    OutreachContactRepository repo = mock(OutreachContactRepository.class);
    List<OutreachContact> saved = new ArrayList<>();
    when(repo.save(any()))
        .thenAnswer(
            inv -> {
              saved.add(inv.getArgument(0));
              return inv.getArgument(0);
            });
    OutreachContact optedOut =
        OutreachContact.builder()
            .email("info@nile.et")
            .name("Nile")
            .sector("Insurance")
            .status(OutreachContact.Status.OPTED_OUT)
            .build();
    when(repo.findByEmailIgnoreCase("info@nile.et")).thenReturn(Optional.of(optedOut));
    when(repo.findByEmailIgnoreCase(any()))
        .thenAnswer(
            inv ->
                "info@nile.et".equals(inv.getArgument(0))
                    ? Optional.of(optedOut)
                    : Optional.empty());

    OutreachService service =
        new OutreachService(
            repo,
            mock(OutreachTemplateRepository.class),
            mock(OutreachMessageRepository.class),
            mock(OutreachTemplateRenderer.class),
            mock(GmailClient.class));

    String csv =
        "name,sector,email,phone,website,city,confidence,source_url,notes,locale,hook\n"
            + "\"Noah Real Estate, PLC\",Real estate developer,Info@Noah.et,6100,https://noah.et,\"Addis Ababa, Bole\",Verified,https://noah.et/contact,\"Email \"\"decoded\"\" from site\",am,Saw your Atlas launch\n"
            + "No Email Co,Contractor,,+251 11 000 000,,,No email,,\n"
            + "Nile Insurance,Insurance,info@nile.et,,,,,,\n";
    ImportResult r =
        service.importCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(r.getAdded()).isEqualTo(1);
    assertThat(r.getSkippedInvalid()).isEqualTo(1);
    assertThat(r.getSkippedProtected()).isEqualTo(1);
    assertThat(saved).hasSize(1);
    OutreachContact c = saved.get(0);
    assertThat(c.getName()).isEqualTo("Noah Real Estate, PLC");
    assertThat(c.getEmail()).isEqualTo("info@noah.et");
    assertThat(c.getCity()).isEqualTo("Addis Ababa, Bole");
    assertThat(c.getNotes()).isEqualTo("Email \"decoded\" from site");
    assertThat(c.getLocale()).isEqualTo("am");
    assertThat(c.getHook()).isEqualTo("Saw your Atlas launch");
    assertThat(c.getStatus()).isEqualTo(OutreachContact.Status.NEW);
    assertThat(optedOut.getStatus()).isEqualTo(OutreachContact.Status.OPTED_OUT);
  }
}
