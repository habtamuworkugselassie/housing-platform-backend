package com.housingplatform.outreach.repository;

import com.housingplatform.outreach.domain.OutreachMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutreachMessageRepository extends JpaRepository<OutreachMessage, UUID> {
  long countBySentAtGreaterThanEqual(LocalDateTime since);

  List<OutreachMessage> findByContactIdOrderBySentAtAsc(UUID contactId);
}
