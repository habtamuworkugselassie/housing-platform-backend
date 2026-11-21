package com.housingplatform.visitor.repository;

import com.housingplatform.visitor.domain.VisitorRegistration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitorRegistrationRepository
    extends JpaRepository<VisitorRegistration, UUID>,
        JpaSpecificationExecutor<VisitorRegistration> {

  Optional<VisitorRegistration> findByCodeIgnoreCase(String code);

  Optional<VisitorRegistration> findByEmailIgnoreCase(String email);

  boolean existsByCodeIgnoreCase(String code);

  long countByStatus(VisitorRegistration.Status status);

  long countByCreatedAtGreaterThanEqual(LocalDateTime since);

  long countByCheckedInAtGreaterThanEqual(LocalDateTime since);

  @Query(
      "select v.visitorType, count(v) from VisitorRegistration v where v.status <> 'CANCELLED' group by v.visitorType")
  List<Object[]> countsByType();

  @Query(
      "select cast(v.createdAt as date), count(v) from VisitorRegistration v where v.createdAt >= :since group by cast(v.createdAt as date) order by cast(v.createdAt as date)")
  List<Object[]> countsByDay(LocalDateTime since);
}
