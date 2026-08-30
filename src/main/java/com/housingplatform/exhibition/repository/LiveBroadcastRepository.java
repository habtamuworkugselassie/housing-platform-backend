package com.housingplatform.exhibition.repository;

import com.housingplatform.exhibition.domain.LiveBroadcast;
import com.housingplatform.exhibition.domain.LiveBroadcast.BroadcastStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveBroadcastRepository extends JpaRepository<LiveBroadcast, UUID> {

  List<LiveBroadcast> findByStatusOrderByUpdatedAtDesc(BroadcastStatus status);

  Page<LiveBroadcast> findByStatus(BroadcastStatus status, Pageable pageable);

  /** Abuse guard: recent go-live requests from one IP. */
  long countByRequesterIpAndCreatedAtAfter(String requesterIp, LocalDateTime after);
}
