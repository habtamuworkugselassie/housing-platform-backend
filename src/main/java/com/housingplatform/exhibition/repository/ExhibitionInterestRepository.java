package com.housingplatform.exhibition.repository;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExhibitionInterestRepository extends JpaRepository<ExhibitionInterest, UUID> {

  @EntityGraph(
      attributePaths = {
        "organization",
        "organization.contact",
        "organization.contact.phones",
        "organization.primaryContact",
        "sponsorship"
      })
  Page<ExhibitionInterest> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Optional<ExhibitionInterest> findFirstByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  java.util.List<ExhibitionInterest> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  /** Exhibitor and partner leads with a package, nudged fewer than {@code max} times. */
  @org.springframework.data.jpa.repository.Query(
      "SELECT e FROM ExhibitionInterest e JOIN FETCH e.sponsorship s LEFT JOIN FETCH e.organization"
          + " WHERE e.interestType IN ('exhibitor', 'partner') AND e.nudgeCount < :max ORDER BY"
          + " e.createdAt ASC")
  java.util.List<ExhibitionInterest> findNudgeCandidates(
      @org.springframework.data.repository.query.Param("max") int max);

  Optional<ExhibitionInterest> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
