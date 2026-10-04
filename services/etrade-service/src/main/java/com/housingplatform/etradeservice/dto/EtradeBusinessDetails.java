package com.housingplatform.etradeservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * A trader's registration on the Ministry of Trade eTrade portal, looked up by TIN. One TIN has one
 * registration (the legal person) and zero or more licensed businesses under it.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EtradeBusinessDetails(
    String tin,
    String businessName,
    String businessNameAmharic,
    EtradeLegalCondition legalCondition,
    /** Commercial registration number, e.g. {@code AA/LK/14/1/3631426/2016}. */
    String registrationNumber,
    LocalDate registrationDate,
    /** Upstream date text, kept when it could not be parsed as a Gregorian date. */
    String registrationDateRaw,
    BigDecimal paidUpCapital,
    List<Associate> associates,
    List<LicensedBusiness> businesses,
    /** True when at least one licence is valid today. */
    boolean hasActiveLicense,
    /** When the record was fetched from eTrade (responses are cached for a while). */
    Instant fetchedAt,
    /** The public page a human can open to see the same record. */
    String sourceUrl) {

  /** Manager / owner / shareholder listed on the registration. */
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Associate(
      String name,
      String nameAmharic,
      String position,
      String mobilePhone,
      String regularPhone,
      /**
       * JPEG as a {@code data:image/jpeg;base64,...} URL; only present when photos were requested.
       */
      String photoDataUrl) {}

  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record LicensedBusiness(
      /** eTrade's business id (a GUID). */
      String id,
      String tradeName,
      String tradeNameAmharic,
      /** Business licence number, e.g. {@code YK/AA/14/672/5990336/2016}. */
      String licenseNumber,
      LocalDate dateRegistered,
      String dateRegisteredRaw,
      /** Day the licence must next be renewed by. */
      LocalDate renewalDate,
      String renewalDateRaw,
      /** Start of the current licence period. */
      LocalDate renewedFrom,
      String renewedFromRaw,
      /** End of the current licence period. */
      LocalDate renewedTo,
      String renewedToRaw,
      LicenseStatus licenseStatus,
      List<Sector> sectors) {}

  /** Line(s) of business the licence covers, as eTrade sub-group codes. */
  public record Sector(Integer code, String description) {}

  public enum LicenseStatus {
    /** Today falls inside the renewed-from / renewed-to window. */
    ACTIVE,
    /** The renewed-to date has passed. */
    EXPIRED,
    /** The licence period starts in the future. */
    NOT_YET_VALID,
    /** eTrade gave no parseable validity dates. */
    UNKNOWN
  }
}
