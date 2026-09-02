package com.housingplatform.exhibition.repository;

import com.housingplatform.exhibition.domain.LiveCohostRequest;
import com.housingplatform.exhibition.domain.LiveCohostRequest.CohostStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveCohostRequestRepository extends JpaRepository<LiveCohostRequest, UUID> {

  List<LiveCohostRequest> findByBroadcastIdAndStatusOrderByCreatedAtAsc(
      UUID broadcastId, CohostStatus status);

  Optional<LiveCohostRequest> findByIdAndBroadcastId(UUID id, UUID broadcastId);

  /** Abuse guard: how many co-host requests this room already holds in a status. */
  long countByBroadcastIdAndStatus(UUID broadcastId, CohostStatus status);
}
