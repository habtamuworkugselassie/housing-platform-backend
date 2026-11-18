package com.housingplatform.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.housingplatform.outreach.domain.OutreachSettings;
import com.housingplatform.outreach.dto.OutreachDtos.SettingsRequest;
import com.housingplatform.outreach.repository.OutreachSettingsRepository;
import com.housingplatform.outreach.service.OutreachProperties;
import com.housingplatform.outreach.service.OutreachSettingsService;
import com.housingplatform.shared.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutreachSettingsServiceTest {

  private final OutreachSettingsRepository repo = mock(OutreachSettingsRepository.class);

  private OutreachSettingsService service() {
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    OutreachProperties props = new OutreachProperties();
    props.setEnabled(false);
    props.setDailyCap(30);
    return new OutreachSettingsService(repo, props);
  }

  @Test
  void firstCallSeedsRowFromEnvironmentDefaults() {
    when(repo.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
    OutreachSettings s = service().current();
    assertThat(s.isEnabled()).isFalse();
    assertThat(s.getDailyCap()).isEqualTo(30);
    assertThat(s.getSendWindowStart()).isEqualTo(9);
  }

  @Test
  void updateChangesOnlyGivenFields() {
    OutreachSettings existing =
        OutreachSettings.builder()
            .enabled(false)
            .dailyCap(30)
            .hourlyCap(8)
            .sendWindowStart(9)
            .sendWindowEnd(17)
            .followUpAfterDays(5)
            .build();
    when(repo.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));
    SettingsRequest r = new SettingsRequest();
    r.setEnabled(true);
    r.setHourlyCap(4);
    OutreachSettings s = service().update(r);
    assertThat(s.isEnabled()).isTrue();
    assertThat(s.getHourlyCap()).isEqualTo(4);
    assertThat(s.getDailyCap()).isEqualTo(30);
  }

  @Test
  void rejectsWindowThatEndsBeforeItStarts() {
    when(repo.findFirstByOrderByCreatedAtAsc())
        .thenReturn(
            Optional.of(OutreachSettings.builder().sendWindowStart(9).sendWindowEnd(17).build()));
    SettingsRequest r = new SettingsRequest();
    r.setSendWindowEnd(8);
    assertThatThrownBy(() -> service().update(r)).isInstanceOf(BusinessException.class);
  }
}
