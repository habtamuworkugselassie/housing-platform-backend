package com.housingplatform.reservation.service;

import com.housingplatform.reservation.domain.Booth;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.dto.ReservationDtos.BoothUpdate;
import com.housingplatform.reservation.dto.ReservationDtos.BoothView;
import com.housingplatform.reservation.repository.BoothRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Booth inventory: who holds what, for how long, and what is left. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BoothService {

  /** A hold lasts as long as the agreement gives to pay in full. */
  public static final int HOLD_BUSINESS_DAYS = 10;

  private final BoothRepository booths;

  /** How many 3×3 m booths a package carries (54, 27, 18, 9 m²; digital-only none). */
  public static int boothsForPackage(String packageType) {
    if (packageType == null) return 1;
    return switch (packageType.toUpperCase(Locale.ROOT)) {
      case "EXCLUSIVE" -> 6;
      case "PLATINUM", "PREMIUM" -> 3;
      case "GOLD" -> 2;
      case "SILVER" -> 1;
      case "SPECIAL" -> 0;
      default -> 1;
    };
  }

  @Transactional(readOnly = true)
  public List<BoothView> list() {
    return booths.findAllByOrderByBlockAscMapRowAscMapColAsc().stream()
        .map(BoothService::toView)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Long> counts() {
    Map<String, Long> m = new LinkedHashMap<>();
    for (Booth.Status s : Booth.Status.values()) m.put(s.name(), 0L);
    for (Object[] row : booths.countsByStatus())
      m.put(((Booth.Status) row[0]).name(), ((Number) row[1]).longValue());
    m.put("TOTAL", booths.count());
    return m;
  }

  @Transactional(readOnly = true)
  public List<String> codesFor(UUID reservationId) {
    return booths.findByReservationId(reservationId).stream().map(Booth::getCode).sorted().toList();
  }

  /**
   * Holds the given booths for a reservation, replacing any it already held. Refuses booths that
   * are not available and more booths than the package carries.
   */
  @Transactional
  public List<String> hold(SponsorshipReservation r, Collection<String> rawCodes) {
    Set<String> codes = normalise(rawCodes);
    int max = boothsForPackage(r.getPackageType());
    if (codes.size() > max) {
      throw new BusinessException(
          max == 0
              ? "This package has no booth (digital participation)"
              : "This package carries " + max + " booth(s); " + codes.size() + " were chosen");
    }
    List<Booth> current = booths.findByReservationId(r.getId());
    for (Booth b : current) {
      if (!codes.contains(b.getCode()) && b.getStatus() == Booth.Status.HELD) free(b);
    }
    if (codes.isEmpty()) {
      booths.saveAll(current);
      return List.of();
    }
    List<Booth> wanted = booths.findByCodeInIgnoreCase(codes);
    if (wanted.size() != codes.size()) throw new BusinessException("Unknown booth code");
    for (Booth b : wanted) {
      boolean mine = b.getReservation() != null && b.getReservation().getId().equals(r.getId());
      if (!mine && b.getStatus() != Booth.Status.AVAILABLE) {
        throw new BusinessException("Booth " + b.getCode() + " is no longer available");
      }
      b.setReservation(r);
      b.setStatus(
          r.getStatus() == SponsorshipReservation.Status.PAID
              ? Booth.Status.SOLD
              : Booth.Status.HELD);
      b.setHoldExpiresAt(
          ReservationService.plusBusinessDays(LocalDateTime.now(), HOLD_BUSINESS_DAYS));
    }
    booths.saveAll(current);
    booths.saveAll(wanted);
    return wanted.stream().map(Booth::getCode).sorted().toList();
  }

  @Transactional
  public void release(UUID reservationId) {
    List<Booth> held = booths.findByReservationId(reservationId);
    held.forEach(this::free);
    booths.saveAll(held);
  }

  @Transactional
  public void sell(UUID reservationId) {
    List<Booth> held = booths.findByReservationId(reservationId);
    for (Booth b : held) {
      b.setStatus(Booth.Status.SOLD);
      b.setHoldExpiresAt(null);
    }
    booths.saveAll(held);
  }

  /** Frees holds whose time ran out. Returns the reservation ids that lost booths. */
  @Transactional
  public Set<UUID> expireHolds() {
    Set<UUID> affected = new LinkedHashSet<>();
    List<Booth> expired =
        booths.findByStatusAndHoldExpiresAtBefore(Booth.Status.HELD, LocalDateTime.now());
    for (Booth b : expired) {
      if (b.getReservation() != null) affected.add(b.getReservation().getId());
      free(b);
    }
    booths.saveAll(expired);
    if (!expired.isEmpty()) log.info("Released {} expired booth hold(s)", expired.size());
    return affected;
  }

  @Transactional
  public BoothView update(String code, BoothUpdate u) {
    Booth b =
        booths.findByCodeInIgnoreCase(List.of(code)).stream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Booth", code));
    if (u.getStatus() != null && !u.getStatus().isBlank()) {
      Booth.Status s = Booth.Status.valueOf(u.getStatus().trim().toUpperCase(Locale.ROOT));
      if (s == Booth.Status.AVAILABLE || s == Booth.Status.BLOCKED) {
        b.setReservation(null);
        b.setHoldExpiresAt(null);
      }
      b.setStatus(s);
    }
    if (u.getBoothType() != null && !u.getBoothType().isBlank()) {
      b.setBoothType(Booth.Type.valueOf(u.getBoothType().trim().toUpperCase(Locale.ROOT)));
    }
    if (u.getNotes() != null) b.setNotes(u.getNotes().isBlank() ? null : u.getNotes().trim());
    return toView(booths.save(b));
  }

  private void free(Booth b) {
    b.setStatus(Booth.Status.AVAILABLE);
    b.setReservation(null);
    b.setHoldExpiresAt(null);
  }

  private static Set<String> normalise(Collection<String> raw) {
    Set<String> out = new LinkedHashSet<>();
    if (raw == null) return out;
    for (String c : raw) {
      if (c == null || c.isBlank()) continue;
      out.add(c.trim().toUpperCase(Locale.ROOT));
    }
    return out;
  }

  static BoothView toView(Booth b) {
    return BoothView.builder()
        .code(b.getCode())
        .block(b.getBlock())
        .col(b.getMapCol())
        .row(b.getMapRow())
        .boothType(b.getBoothType().name())
        .sizeSqm(b.getSizeSqm())
        .status(b.getStatus().name())
        .reservationId(b.getReservation() != null ? b.getReservation().getId() : null)
        .holdExpiresAt(b.getHoldExpiresAt())
        .notes(b.getNotes())
        .build();
  }
}
