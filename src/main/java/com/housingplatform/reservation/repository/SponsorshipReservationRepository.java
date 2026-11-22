package com.housingplatform.reservation.repository;

import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.domain.SponsorshipReservation.Status;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SponsorshipReservationRepository
    extends JpaRepository<SponsorshipReservation, UUID>,
        JpaSpecificationExecutor<SponsorshipReservation> {

  Optional<SponsorshipReservation> findByReferenceIgnoreCase(String reference);

  boolean existsByReferenceIgnoreCase(String reference);

  @Query(
      "select r.status, count(r), coalesce(sum(r.amount),0), coalesce(sum(r.amountPaid),0) from SponsorshipReservation r group by r.status")
  List<Object[]> totalsByStatus();

  List<SponsorshipReservation> findByStatusInAndStatusChangedAtBefore(
      List<Status> statuses, LocalDateTime before);

  List<SponsorshipReservation> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  List<SponsorshipReservation> findByOrganizationIsNull();

  List<SponsorshipReservation> findByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

  List<SponsorshipReservation> findByInterestIsNull();

  List<SponsorshipReservation> findByStatusInOrderByCreatedAtDesc(
      java.util.Collection<SponsorshipReservation.Status> statuses);

  List<SponsorshipReservation> findByStatusInAndPaymentDueAtBefore(
      java.util.Collection<SponsorshipReservation.Status> statuses, java.time.LocalDateTime before);
}
