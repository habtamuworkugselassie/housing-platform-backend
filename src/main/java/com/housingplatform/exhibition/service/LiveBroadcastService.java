package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.dto.AdminLiveBroadcastResponse;
import com.housingplatform.exhibition.dto.LiveBroadcastResponse;
import com.housingplatform.exhibition.dto.LiveGoLiveRequest;
import com.housingplatform.exhibition.dto.LiveTokenResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LiveBroadcastService {

  /** A visitor/exhibitor/organizer requests to go live; held until an organizer approves. */
  LiveBroadcastResponse requestGoLive(LiveGoLiveRequest request, String ip);

  /** Broadcaster publish token — issued only when the request is APPROVED (or already LIVE). */
  LiveTokenResponse publishToken(UUID id, String ip);

  /** Subscribe-only viewer token for a LIVE broadcast (plus HLS url when available). */
  LiveTokenResponse viewerToken(UUID id);

  /** Public status of a single broadcast (so a broadcaster can poll for approval). */
  LiveBroadcastResponse get(UUID id);

  /** Public wall: currently LIVE broadcasts. */
  List<LiveBroadcastResponse> listLive();

  Page<AdminLiveBroadcastResponse> adminList(String status, Pageable pageable);

  AdminLiveBroadcastResponse approve(UUID id);

  AdminLiveBroadcastResponse reject(UUID id);

  /** Kill-switch: force-close the room and mark it ENDED. */
  AdminLiveBroadcastResponse end(UUID id);
}
