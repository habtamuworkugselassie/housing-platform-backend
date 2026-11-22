package com.housingplatform.reservation.service;

import java.util.UUID;

/** A reservation reached DEPOSIT_PAID or PAID; the exhibitor account can be provisioned. */
public record ReservationPaidEvent(UUID reservationId) {}
