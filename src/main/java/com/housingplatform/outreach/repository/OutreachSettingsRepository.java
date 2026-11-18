package com.housingplatform.outreach.repository;

import com.housingplatform.outreach.domain.OutreachSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutreachSettingsRepository extends JpaRepository<OutreachSettings, UUID> {
  Optional<OutreachSettings> findFirstByOrderByCreatedAtAsc();
}
