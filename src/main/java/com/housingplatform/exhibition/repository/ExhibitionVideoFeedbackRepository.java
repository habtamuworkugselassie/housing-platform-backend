package com.housingplatform.exhibition.repository;

import com.housingplatform.exhibition.domain.ExhibitionVideoFeedback;
import com.housingplatform.exhibition.domain.ExhibitionVideoFeedback.FeedbackStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExhibitionVideoFeedbackRepository
    extends JpaRepository<ExhibitionVideoFeedback, UUID> {

  Page<ExhibitionVideoFeedback> findByStatus(FeedbackStatus status, Pageable pageable);

  /** Abuse guard: how many videos this IP submitted since the given instant. */
  long countBySubmitterIpAndCreatedAtAfter(String submitterIp, LocalDateTime after);
}
