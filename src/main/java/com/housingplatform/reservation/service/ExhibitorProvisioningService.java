package com.housingplatform.reservation.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.PasswordResetToken;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.PasswordResetTokenRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.OrganizationPrimaryUserProvisioningService;
import com.housingplatform.identity.service.SponsorshipService;
import com.housingplatform.reservation.domain.SponsorshipReservation;
import com.housingplatform.reservation.domain.SponsorshipReservation.Status;
import com.housingplatform.reservation.dto.ReservationDtos.AdminRow;
import com.housingplatform.reservation.repository.SponsorshipReservationRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a paid reservation into an exhibitor account: the organization (found by the contact's
 * existing account or email, or created), its primary user, an approved sponsorship for the
 * reserved package, and an invitation email with a set-password link. Idempotent: a reservation
 * already linked to an organization is left alone unless a re-invite is asked for.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExhibitorProvisioningService {

  private static final long INVITE_HOURS = 72;

  private final SponsorshipReservationRepository reservations;
  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final PasswordResetTokenRepository tokens;
  private final OrganizationPrimaryUserProvisioningService primaryUsers;
  private final SponsorshipService sponsorships;
  private final BoothService boothService;
  private final ExhibitorMailService mail;
  private final ReservationOrganizationLinker linker;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onPaid(ReservationPaidEvent event) {
    try {
      provision(event.reservationId(), false);
    } catch (RuntimeException e) {
      log.warn(
          "Exhibitor account for reservation {} not created: {}",
          event.reservationId(),
          e.getMessage());
    }
  }

  /**
   * @param resendInvite when the reservation is already linked, send the invitation again (or a
   *     "your portal is ready" note when the user already has a password).
   */
  @Transactional
  public AdminRow provision(UUID reservationId, boolean resendInvite) {
    SponsorshipReservation r =
        reservations
            .findById(reservationId)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));
    if (r.getStatus() != Status.PAID && r.getStatus() != Status.DEPOSIT_PAID) {
      throw new BusinessException("The exhibitor account is created once a payment is recorded");
    }
    // The organization is linked at reservation time; the invitation marks provisioning done.
    if (r.getExhibitorInvitedAt() != null && !resendInvite) {
      return ReservationService.toRow(r, boothService.codesFor(r.getId()));
    }

    Organization org = r.getOrganization() != null ? r.getOrganization() : linker.link(r, null);
    User user = primaryUsers.linkExhibitionLeadUser(org, r.getEmail(), r.getPhone());
    if (user.getStatus() == User.UserStatus.PENDING_VERIFICATION) {
      String[] names = splitName(r.getContactName());
      user.setFirstName(names[0]);
      user.setLastName(names[1]);
      users.save(user);
    }
    if (r.getSponsorship() != null) {
      sponsorships.grantFromReservation(
          org.getId(), r.getSponsorship().getId(), "Reservation " + r.getReference());
    }
    r.setOrganization(org);

    boolean hasPassword = Boolean.TRUE.equals(user.getPasswordSet());
    String token = hasPassword ? null : newInviteToken(user);
    r.setExhibitorInvitedAt(LocalDateTime.now());
    SponsorshipReservation saved = reservations.save(r);
    mail.sendInvite(saved, org, user, token);
    log.info(
        "Exhibitor account for {} ({}): organization {}, user {}",
        r.getReference(),
        r.getCompany(),
        org.getId(),
        user.getEmail());
    return ReservationService.toRow(saved, boothService.codesFor(saved.getId()));
  }

  private String newInviteToken(User user) {
    tokens.deleteByUserId(user.getId());
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    tokens.save(
        PasswordResetToken.builder()
            .token(token)
            .userId(user.getId())
            .expiresAt(Instant.now().plusSeconds(INVITE_HOURS * 3600L))
            .build());
    return token;
  }

  public static String[] splitName(String full) {
    String n = full == null ? "" : full.trim().replaceAll("\\s+", " ");
    int i = n.indexOf(' ');
    if (n.isEmpty()) return new String[] {"Exhibitor", "Contact"};
    if (i < 0) return new String[] {n, "-"};
    return new String[] {n.substring(0, i), n.substring(i + 1)};
  }
}
