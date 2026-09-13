package com.housingplatform.exhibition.email;

import java.util.UUID;

/**
 * Published when a registration is saved. Carries the id only: the listener runs after the
 * transaction commits, so it must read the committed row rather than an entity that may still be
 * managed by the transaction that created it.
 */
public record ExhibitionInterestRegisteredEvent(UUID interestId) {}
