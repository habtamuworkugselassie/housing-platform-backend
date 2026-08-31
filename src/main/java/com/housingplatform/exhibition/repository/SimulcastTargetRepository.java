package com.housingplatform.exhibition.repository;

import com.housingplatform.exhibition.domain.SimulcastTarget;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulcastTargetRepository extends JpaRepository<SimulcastTarget, UUID> {
  List<SimulcastTarget> findAllByOrderByCreatedAtAsc();
}
