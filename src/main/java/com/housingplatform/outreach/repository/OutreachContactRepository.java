package com.housingplatform.outreach.repository;

import com.housingplatform.outreach.domain.OutreachContact;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OutreachContactRepository
    extends JpaRepository<OutreachContact, UUID>, JpaSpecificationExecutor<OutreachContact> {

  Optional<OutreachContact> findByEmailIgnoreCase(String email);

  /** Contacts whose next step is due, oldest due first. */
  List<OutreachContact> findTop20ByStatusInAndNextActionAtLessThanEqualOrderByNextActionAtAsc(
      List<OutreachContact.Status> statuses, LocalDateTime now);

  List<OutreachContact> findByStatusInAndLastSentAtIsNotNull(List<OutreachContact.Status> statuses);

  long countByStatus(OutreachContact.Status status);

  List<OutreachContact> findTop20ByStatusAndStepOrderByCreatedAtAsc(
      OutreachContact.Status status, int step);

  List<OutreachContact> findByStatusAndStep(OutreachContact.Status status, int step);

  @Query("select c.status, count(c) from OutreachContact c group by c.status")
  List<Object[]> countsByStatus();
}
