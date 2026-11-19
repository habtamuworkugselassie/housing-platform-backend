package com.housingplatform.outreach.service;

import com.housingplatform.outreach.domain.OutreachSettings;
import com.housingplatform.outreach.dto.OutreachDtos.SettingsRequest;
import com.housingplatform.outreach.repository.OutreachSettingsRepository;
import com.housingplatform.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The switch and caps live in the database so an admin can change them without a restart. */
@Service
@RequiredArgsConstructor
public class OutreachSettingsService {

  private final OutreachSettingsRepository repository;
  private final OutreachProperties props;

  /** The stored row, created from the environment defaults on first use. */
  @Transactional
  public OutreachSettings current() {
    return repository
        .findFirstByOrderByCreatedAtAsc()
        .orElseGet(
            () ->
                repository.save(
                    OutreachSettings.builder()
                        .enabled(props.isEnabled())
                        .dailyCap(props.getDailyCap())
                        .hourlyCap(props.getHourlyCap())
                        .sendWindowStart(props.getSendWindowStart())
                        .sendWindowEnd(props.getSendWindowEnd())
                        .followUpAfterDays(props.getFollowUpAfterDays())
                        .autoQueueNew(true)
                        .build()));
  }

  @Transactional
  public OutreachSettings update(SettingsRequest r) {
    OutreachSettings s = current();
    if (r.getEnabled() != null) s.setEnabled(r.getEnabled());
    if (r.getAutoQueueNew() != null) s.setAutoQueueNew(r.getAutoQueueNew());
    if (r.getDailyCap() != null) s.setDailyCap(range(r.getDailyCap(), 1, 200, "Daily cap"));
    if (r.getHourlyCap() != null) s.setHourlyCap(range(r.getHourlyCap(), 1, 50, "Hourly cap"));
    if (r.getSendWindowStart() != null) {
      s.setSendWindowStart(range(r.getSendWindowStart(), 0, 23, "Window start"));
    }
    if (r.getSendWindowEnd() != null)
      s.setSendWindowEnd(range(r.getSendWindowEnd(), 1, 24, "Window end"));
    if (r.getFollowUpAfterDays() != null) {
      s.setFollowUpAfterDays(range(r.getFollowUpAfterDays(), 1, 30, "Follow-up days"));
    }
    if (s.getSendWindowEnd() <= s.getSendWindowStart()) {
      throw new BusinessException("The send window must end after it starts");
    }
    return repository.save(s);
  }

  private static int range(int v, int min, int max, String what) {
    if (v < min || v > max)
      throw new BusinessException(what + " must be between " + min + " and " + max);
    return v;
  }
}
