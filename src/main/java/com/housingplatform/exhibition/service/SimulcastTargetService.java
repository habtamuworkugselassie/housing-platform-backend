package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.domain.SimulcastTarget;
import com.housingplatform.exhibition.dto.SimulcastTargetRequest;
import com.housingplatform.exhibition.dto.SimulcastTargetResponse;
import com.housingplatform.exhibition.repository.SimulcastTargetRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for reusable social RTMP destinations (simulcast targets). */
@Service
@RequiredArgsConstructor
public class SimulcastTargetService {

  private final SimulcastTargetRepository repository;

  @Transactional(readOnly = true)
  public List<SimulcastTargetResponse> list() {
    return repository.findAllByOrderByCreatedAtAsc().stream()
        .map(SimulcastTargetResponse::from)
        .toList();
  }

  @Transactional
  public SimulcastTargetResponse create(SimulcastTargetRequest req) {
    SimulcastTarget t =
        SimulcastTarget.builder()
            .platform(parsePlatform(req.platform()))
            .label(req.label().trim())
            .rtmpUrl(req.rtmpUrl().trim())
            .streamKey(req.streamKey() == null ? "" : req.streamKey().trim())
            .enabled(req.enabled() == null || req.enabled())
            .build();
    if (t.getStreamKey().isBlank()) {
      throw new BusinessException("A stream key is required.");
    }
    return SimulcastTargetResponse.from(repository.save(t));
  }

  @Transactional
  public SimulcastTargetResponse update(UUID id, SimulcastTargetRequest req) {
    SimulcastTarget t = find(id);
    t.setPlatform(parsePlatform(req.platform()));
    t.setLabel(req.label().trim());
    t.setRtmpUrl(req.rtmpUrl().trim());
    // Blank key on update = keep the existing secret.
    if (req.streamKey() != null && !req.streamKey().isBlank()) {
      t.setStreamKey(req.streamKey().trim());
    }
    if (req.enabled() != null) {
      t.setEnabled(req.enabled());
    }
    return SimulcastTargetResponse.from(repository.save(t));
  }

  @Transactional
  public void delete(UUID id) {
    repository.delete(find(id));
  }

  private SimulcastTarget find(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Simulcast target", id));
  }

  private static SimulcastTarget.Platform parsePlatform(String platform) {
    try {
      return SimulcastTarget.Platform.valueOf(platform.trim().toUpperCase());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new BusinessException("Unknown platform: " + platform);
    }
  }
}
