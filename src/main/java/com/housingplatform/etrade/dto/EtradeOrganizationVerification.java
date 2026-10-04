package com.housingplatform.etrade.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import lombok.Builder;

/**
 * Result of checking one of our organizations against eTrade: the record found for the TIN the
 * organization declared, and how its other declared numbers compare with it. Produced on demand for
 * admins reviewing verification documents; nothing is persisted.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EtradeOrganizationVerification(
    UUID organizationId,
    /** The TIN as stored on the organization (normalised). */
    String tin,
    /** False when eTrade has no registration under that TIN. */
    boolean found,
    EtradeBusinessDetails details,
    Checks checks) {

  /**
   * Each flag is null when the comparison could not be made (e.g. the organization has no value).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Checks(
      /** Organization name equals the registered business name or one of its trade names. */
      Boolean nameMatches,
      /** Organization's licence number equals one of the licences under the TIN. */
      Boolean licenseNumberMatches,
      /** Organization's business registration number equals eTrade's registration number. */
      Boolean registrationNumberMatches,
      /** At least one licence under the TIN is valid today. */
      Boolean licenseActive) {}
}
