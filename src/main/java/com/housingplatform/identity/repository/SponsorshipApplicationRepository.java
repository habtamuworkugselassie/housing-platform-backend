package com.housingplatform.identity.repository;

import com.housingplatform.identity.domain.SponsorshipApplication;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SponsorshipApplicationRepository
    extends JpaRepository<SponsorshipApplication, UUID> {

  List<SponsorshipApplication> findByOrganizationId(UUID organizationId);

  List<SponsorshipApplication> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  List<SponsorshipApplication> findBySponsorshipId(UUID sponsorshipId);

  List<SponsorshipApplication> findByStatus(SponsorshipApplication.ApplicationStatus status);

  List<SponsorshipApplication> findBySponsorshipIdAndOrganizationIdAndStatus(UUID sponsorship_id, UUID organization_id, SponsorshipApplication.ApplicationStatus status);

  @Query(
      "SELECT sa FROM SponsorshipApplication sa WHERE sa.organization.id = :organizationId "
          + "AND sa.status = 'APPROVED' "
          + "AND sa.startDate <= :now AND sa.endDate >= :now "
          + "ORDER BY sa.sponsorship.type DESC, sa.startDate DESC")
  Optional<SponsorshipApplication> findActiveApplication(
      @Param("organizationId") UUID organizationId, @Param("now") LocalDateTime now);

  @Query(
      "SELECT sa FROM SponsorshipApplication sa WHERE sa.status = 'APPROVED' "
          + "AND sa.startDate <= :now AND sa.endDate >= :now "
          + "ORDER BY sa.sponsorship.type DESC, sa.startDate DESC")
  List<SponsorshipApplication> findAllActiveApplications(@Param("now") LocalDateTime now);

  @Query(
      "SELECT sa FROM SponsorshipApplication sa WHERE sa.organization.id = :organizationId "
          + "AND sa.sponsorship.id = :sponsorshipId "
          + "AND sa.status = 'APPROVED' "
          + "AND sa.startDate <= :now AND sa.endDate >= :now")
  Optional<SponsorshipApplication> findActiveApplicationBySponsorship(
      @Param("organizationId") UUID organizationId,
      @Param("sponsorshipId") UUID sponsorshipId,
      @Param("now") LocalDateTime now);
}
