package com.housingplatform.purchase.repository;

import com.housingplatform.purchase.domain.AgreementTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgreementTemplateRepository extends JpaRepository<AgreementTemplate, UUID> {

  List<AgreementTemplate> findByActiveTrueAndIssueTriggerOrderBySequenceAscTemplateVersionDesc(
      AgreementTemplate.IssueTrigger trigger);

  Optional<AgreementTemplate> findFirstByTypeAndActiveTrueOrderByTemplateVersionDesc(
      AgreementTemplate.AgreementType type);

  Optional<AgreementTemplate> findFirstByTypeOrderByTemplateVersionDesc(
      AgreementTemplate.AgreementType type);

  List<AgreementTemplate> findAllByOrderByTypeAscTemplateVersionDesc();
}
