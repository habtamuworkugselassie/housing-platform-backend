package com.housingplatform.outreach.repository;

import com.housingplatform.outreach.domain.OutreachContact;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutreachContactRepository extends JpaRepository<OutreachContact, UUID> {

  Optional<OutreachContact> findByEmailIgnoreCase(String email);

  @Query(
      """
      select c from OutreachContact c
      where (:status is null or c.status = :status)
        and (:sector is null or lower(c.sector) = lower(:sector))
        and (:q is null or lower(c.name) like lower(concat('%', :q, '%'))
             or lower(c.email) like lower(concat('%', :q, '%')))
      order by c.updatedAt desc
      """)
  Page<OutreachContact> search(
      @Param("status") OutreachContact.Status status,
      @Param("sector") String sector,
      @Param("q") String q,
      Pageable pageable);

  /** Contacts whose next step is due, oldest due first. */
  List<OutreachContact> findTop20ByStatusInAndNextActionAtLessThanEqualOrderByNextActionAtAsc(
      List<OutreachContact.Status> statuses, LocalDateTime now);

  List<OutreachContact> findByStatusInAndThreadIdIsNotNull(List<OutreachContact.Status> statuses);

  long countByStatus(OutreachContact.Status status);

  @Query("select c.status, count(c) from OutreachContact c group by c.status")
  List<Object[]> countsByStatus();
}
