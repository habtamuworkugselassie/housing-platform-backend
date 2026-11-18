package com.housingplatform.outreach.repository;

import com.housingplatform.outreach.domain.OutreachTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutreachTemplateRepository extends JpaRepository<OutreachTemplate, UUID> {
  Optional<OutreachTemplate> findByTemplateKeyAndLocale(String templateKey, String locale);

  List<OutreachTemplate> findAllByOrderByTemplateKeyAscLocaleAsc();
}
