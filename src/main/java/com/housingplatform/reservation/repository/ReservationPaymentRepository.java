package com.housingplatform.reservation.repository;

import com.housingplatform.reservation.domain.ReservationPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationPaymentRepository extends JpaRepository<ReservationPayment, UUID> {
  Optional<ReservationPayment> findByTxRef(String txRef);

  List<ReservationPayment> findByReservationIdOrderByCreatedAtDesc(UUID reservationId);

  List<ReservationPayment> findByStatus(ReservationPayment.Status status);
}
