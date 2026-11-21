package com.housingplatform.reservation.repository;

import com.housingplatform.reservation.domain.Booth;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BoothRepository extends JpaRepository<Booth, UUID> {

  List<Booth> findAllByOrderByBlockAscMapRowAscMapColAsc();

  List<Booth> findByCodeInIgnoreCase(Collection<String> codes);

  List<Booth> findByReservationId(UUID reservationId);

  List<Booth> findByStatusAndHoldExpiresAtBefore(Booth.Status status, LocalDateTime before);

  long countByStatus(Booth.Status status);

  @Query("select b.status, count(b) from Booth b group by b.status")
  List<Object[]> countsByStatus();
}
