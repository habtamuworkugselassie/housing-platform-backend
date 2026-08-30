package com.housingplatform.exhibition.service.impl;

import com.housingplatform.exhibition.config.LiveKitProperties;
import com.housingplatform.exhibition.domain.LiveBroadcast;
import com.housingplatform.exhibition.domain.LiveBroadcast.BroadcastStatus;
import com.housingplatform.exhibition.domain.LiveBroadcast.BroadcasterRole;
import com.housingplatform.exhibition.dto.AdminLiveBroadcastResponse;
import com.housingplatform.exhibition.dto.LiveBroadcastResponse;
import com.housingplatform.exhibition.dto.LiveGoLiveRequest;
import com.housingplatform.exhibition.dto.LiveTokenResponse;
import com.housingplatform.exhibition.repository.LiveBroadcastRepository;
import com.housingplatform.exhibition.service.LiveBroadcastService;
import com.housingplatform.exhibition.service.LiveKitRoomService;
import com.housingplatform.exhibition.service.LiveKitTokenService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LiveBroadcastServiceImpl implements LiveBroadcastService {

  private static final long PUBLISH_TTL_SECONDS = 3 * 3600L;
  private static final long VIEWER_TTL_SECONDS = 3 * 3600L;
  private static final int RATE_LIMIT_WINDOW_MINUTES = 60;
  private static final int RATE_LIMIT_MAX_PER_WINDOW = 5;

  private final LiveBroadcastRepository repository;
  private final LiveKitTokenService tokenService;
  private final LiveKitRoomService roomService;
  private final LiveKitProperties properties;

  @Override
  @Transactional
  public LiveBroadcastResponse requestGoLive(LiveGoLiveRequest request, String ip) {
    String name = request.name() == null ? "" : request.name().trim();
    String title = request.title() == null ? "" : request.title().trim();
    if (name.isBlank()) {
      throw new BusinessException("Your name is required.");
    }
    if (title.isBlank()) {
      throw new BusinessException("A stream title is required.");
    }
    if (ip != null && !ip.isBlank()) {
      long recent =
          repository.countByRequesterIpAndCreatedAtAfter(
              ip, LocalDateTime.now().minusMinutes(RATE_LIMIT_WINDOW_MINUTES));
      if (recent >= RATE_LIMIT_MAX_PER_WINDOW) {
        throw new BusinessException("Too many go-live requests from this device. Try again later.");
      }
    }
    String email = request.email() == null ? null : request.email().trim();
    String company =
        request.company() == null || request.company().isBlank() ? null : request.company().trim();

    LiveBroadcast saved =
        repository.save(
            LiveBroadcast.builder()
                .room("ebc-" + UUID.randomUUID().toString().substring(0, 12))
                .title(title)
                .broadcasterName(name)
                .broadcasterEmail(email == null || email.isBlank() ? null : email)
                .broadcasterRole(parseRole(request.role()))
                .companyName(company)
                .status(BroadcastStatus.REQUESTED)
                .requesterIp(ip)
                .build());
    return LiveBroadcastResponse.from(saved);
  }

  @Override
  @Transactional
  public LiveTokenResponse publishToken(UUID id, String ip) {
    LiveBroadcast b = find(id);
    if (b.getStatus() != BroadcastStatus.APPROVED && b.getStatus() != BroadcastStatus.LIVE) {
      throw new BusinessException("This broadcast has not been approved to go live.");
    }
    if (b.getStatus() == BroadcastStatus.APPROVED) {
      b.setStatus(BroadcastStatus.LIVE);
      repository.save(b);
    }
    String token =
        tokenService.mint(
            "pub-" + b.getId(), b.getBroadcasterName(), b.getRoom(), true, false, PUBLISH_TTL_SECONDS);
    return new LiveTokenResponse(properties.getUrl(), token, b.getRoom(), b.getHlsUrl());
  }

  @Override
  @Transactional(readOnly = true)
  public LiveTokenResponse viewerToken(UUID id) {
    LiveBroadcast b = find(id);
    if (b.getStatus() != BroadcastStatus.LIVE) {
      throw new BusinessException("This broadcast is not live.");
    }
    String token =
        tokenService.mint(
            "view-" + UUID.randomUUID().toString().substring(0, 8),
            "viewer",
            b.getRoom(),
            false,
            false,
            VIEWER_TTL_SECONDS);
    return new LiveTokenResponse(properties.getUrl(), token, b.getRoom(), b.getHlsUrl());
  }

  @Override
  @Transactional(readOnly = true)
  public LiveBroadcastResponse get(UUID id) {
    return LiveBroadcastResponse.from(find(id));
  }

  @Override
  @Transactional(readOnly = true)
  public List<LiveBroadcastResponse> listLive() {
    return repository.findByStatusOrderByUpdatedAtDesc(BroadcastStatus.LIVE).stream()
        .map(LiveBroadcastResponse::from)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AdminLiveBroadcastResponse> adminList(String status, Pageable pageable) {
    BroadcastStatus parsed = parseStatus(status);
    Page<LiveBroadcast> page =
        parsed == null ? repository.findAll(pageable) : repository.findByStatus(parsed, pageable);
    return page.map(AdminLiveBroadcastResponse::from);
  }

  @Override
  @Transactional
  public AdminLiveBroadcastResponse approve(UUID id) {
    LiveBroadcast b = find(id);
    if (b.getStatus() == BroadcastStatus.REQUESTED || b.getStatus() == BroadcastStatus.REJECTED) {
      b.setStatus(BroadcastStatus.APPROVED);
    }
    return AdminLiveBroadcastResponse.from(repository.save(b));
  }

  @Override
  @Transactional
  public AdminLiveBroadcastResponse reject(UUID id) {
    LiveBroadcast b = find(id);
    if (b.getStatus() == BroadcastStatus.LIVE) {
      roomService.deleteRoom(b.getRoom());
    }
    b.setStatus(BroadcastStatus.REJECTED);
    return AdminLiveBroadcastResponse.from(repository.save(b));
  }

  @Override
  @Transactional
  public AdminLiveBroadcastResponse end(UUID id) {
    LiveBroadcast b = find(id);
    roomService.deleteRoom(b.getRoom());
    b.setStatus(BroadcastStatus.ENDED);
    return AdminLiveBroadcastResponse.from(repository.save(b));
  }

  private LiveBroadcast find(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Live broadcast", id));
  }

  private static BroadcasterRole parseRole(String role) {
    String r = role == null ? "" : role.trim().toUpperCase();
    if ("EXHIBITOR".equals(r)) return BroadcasterRole.EXHIBITOR;
    if ("ORGANIZER".equals(r)) return BroadcasterRole.ORGANIZER;
    return BroadcasterRole.VISITOR;
  }

  private static BroadcastStatus parseStatus(String status) {
    if (status == null || status.isBlank()) return null;
    try {
      return BroadcastStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BusinessException("Unknown status: " + status);
    }
  }
}
