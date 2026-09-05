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
  LiveTokenResponse viewerToken(UUID id, String viewerName);

  /** Public status of a single broadcast (so a broadcaster can poll for approval). */
  LiveBroadcastResponse get(UUID id);

  /** Public wall: currently LIVE broadcasts. */
  List<LiveBroadcastResponse> listLive();

  /** Public replays: recently-ended broadcasts that produced a recording. */
  List<LiveBroadcastResponse> listReplays();

  Page<AdminLiveBroadcastResponse> adminList(String status, Pageable pageable);

  /** Provision an RTMP/WHIP ingress for a broadcast so an external encoder (pro camera) can feed it. */
  com.housingplatform.exhibition.dto.IngressResponse createIngress(UUID id, String type);

  /** Start a real-time RTMP simulcast of a LIVE broadcast to the given targets (or all enabled). */
  AdminLiveBroadcastResponse startSimulcast(UUID id, java.util.List<UUID> targetIds);

  /** Stop the running social simulcast for a broadcast. */
  AdminLiveBroadcastResponse stopSimulcast(UUID id);

  AdminLiveBroadcastResponse approve(UUID id);

  AdminLiveBroadcastResponse reject(UUID id);

  /** Kill-switch: force-close the room and mark it ENDED. */
  AdminLiveBroadcastResponse end(UUID id);

  /**
   * Broadcaster ends their own stream (Stop broadcasting / tab close): finalizes the recording,
   * stops simulcast, closes the room and marks it ENDED. Idempotent.
   */
  LiveBroadcastResponse endByBroadcaster(UUID id, String ip);

  // --- Co-hosting: approved viewers publish into the same live room -----------

  /** A viewer asks to co-host a LIVE broadcast; held PENDING until the broadcaster approves. */
  com.housingplatform.exhibition.dto.CohostRequestResponse requestCohost(UUID broadcastId, String name);

  /** Pending co-host requests for a broadcast (the broadcaster's moderation queue). */
  List<com.housingplatform.exhibition.dto.CohostRequestResponse> listPendingCohosts(UUID broadcastId);

  /** Broadcaster approves/denies a co-host request. */
  com.housingplatform.exhibition.dto.CohostRequestResponse decideCohost(
      UUID broadcastId, UUID requestId, boolean approve, String ip);

  /** Co-host publish token — issued only once the request is APPROVED (viewer polls for it). */
  LiveTokenResponse cohostToken(UUID broadcastId, UUID requestId);
}
