package com.housingplatform.exhibition.repository;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
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
}
