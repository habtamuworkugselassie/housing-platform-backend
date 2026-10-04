package com.housingplatform.etrade.service;

import com.housingplatform.etrade.EtradeProperties;
import com.housingplatform.etrade.client.EtradeServiceClient;
import com.housingplatform.etrade.dto.EtradeBusinessDetails;
import com.housingplatform.etrade.dto.EtradeBusinessDetails.LicenseStatus;
import com.housingplatform.etrade.dto.EtradeOrganizationVerification;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ForbiddenOperationException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Looks businesses up through the standalone etrade-service and compares what an organization
 * declared with the public record. Caching and the eTrade upstream quirks live in that service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EtradeBusinessService {

  /** Ethiopian TINs are ten digits. */
  private static final Pattern TIN = Pattern.compile("\\d{10}");

  private final EtradeProperties properties;
  private final EtradeServiceClient client;
  private final OrganizationRepository organizationRepository;

  /**
   * Fetches the registration and licensed businesses behind a TIN.
   *
   * @throws BusinessException when the TIN is malformed or the integration is disabled
   * @throws ResourceNotFoundException when eTrade has no registration under the TIN
   * @throws com.housingplatform.etrade.EtradeUnavailableException when eTrade cannot be queried
   */
  public EtradeBusinessDetails getBusinessDetails(String tin, boolean includePhotos) {
    String normalized = normalizeTin(tin);
    return fetch(normalized, includePhotos)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No business is registered on eTrade under TIN " + normalized));
  }

  /** Same as {@link #getBusinessDetails} but empty instead of 404 when unknown. */
  public Optional<EtradeBusinessDetails> findBusinessDetails(String tin, boolean includePhotos) {
    return fetch(normalizeTin(tin), includePhotos);
  }

  /**
   * Checks an organization's declared TIN, name, licence and registration numbers against eTrade.
   * Admins may check any organization; a primary contact may check their own.
   */
  public EtradeOrganizationVerification verifyOrganization(UUID organizationId) {
    Organization org =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Organization not found: " + organizationId));
    assertMayVerify(org);
    if (org.getTinNumber() == null || org.getTinNumber().isBlank()) {
      throw new BusinessException("The organization has no TIN number to verify");
    }
    String tin = normalizeTin(org.getTinNumber());
    Optional<EtradeBusinessDetails> details = fetch(tin, false);
    return EtradeOrganizationVerification.builder()
        .organizationId(org.getId())
        .tin(tin)
        .found(details.isPresent())
        .details(details.orElse(null))
        .checks(details.map(d -> compare(org, d)).orElse(null))
        .build();
  }

  static EtradeOrganizationVerification.Checks compare(Organization org, EtradeBusinessDetails d) {
    Boolean name = null;
    if (hasText(org.getName())) {
      String ours = fold(org.getName());
      name =
          ours.equals(fold(d.businessName()))
              || ours.equals(fold(d.businessNameAmharic()))
              || d.businesses().stream()
                  .anyMatch(
                      b ->
                          ours.equals(fold(b.tradeName()))
                              || ours.equals(fold(b.tradeNameAmharic())));
    }
    Boolean license = null;
    if (hasText(org.getLicenseNumber())) {
      String ours = foldNumber(org.getLicenseNumber());
      license = d.businesses().stream().anyMatch(b -> ours.equals(foldNumber(b.licenseNumber())));
    }
    Boolean registration = null;
    if (hasText(org.getBusinessRegistrationNumber())) {
      registration =
          foldNumber(org.getBusinessRegistrationNumber())
              .equals(foldNumber(d.registrationNumber()));
    }
    Boolean active =
        d.businesses().isEmpty()
            ? null
            : d.businesses().stream().anyMatch(b -> b.licenseStatus() == LicenseStatus.ACTIVE);
    return new EtradeOrganizationVerification.Checks(name, license, registration, active);
  }

  private void assertMayVerify(Organization org) {
    if (UserContext.isAdmin()) {
      return;
    }
    UUID me = UserContext.getCurrentUserIdOrNull();
    boolean primaryContact =
        me != null && org.getPrimaryContact() != null && me.equals(org.getPrimaryContact().getId());
    boolean member =
        UserContext.getCurrentUserOrganizationId().filter(org.getId()::equals).isPresent();
    if (!primaryContact && !member) {
      throw new ForbiddenOperationException(
          "Only admins or members of the organization can run its eTrade check");
    }
  }

  /** Strips separators and validates the ten-digit shape. */
  public static String normalizeTin(String tin) {
    if (tin == null) {
      throw new BusinessException("TIN is required");
    }
    String digits = tin.replaceAll("[\\s\\-/.]", "");
    if (!TIN.matcher(digits).matches()) {
      throw new BusinessException("TIN must be exactly 10 digits, e.g. 0081723672");
    }
    return digits;
  }

  private Optional<EtradeBusinessDetails> fetch(String tin, boolean includePhotos) {
    if (!properties.isEnabled()) {
      throw new BusinessException("eTrade business lookup is not enabled on this server");
    }
    return client.findBusiness(tin, includePhotos);
  }

  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }

  /** Case- and whitespace-insensitive comparison key for names. */
  private static String fold(String s) {
    if (s == null) {
      return "";
    }
    return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  /** Numbers like {@code AA/LK/14/1/3631426/2016} are compared ignoring case, spaces and dashes. */
  private static String foldNumber(String s) {
    if (s == null) {
      return "";
    }
    return s.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
  }
}
