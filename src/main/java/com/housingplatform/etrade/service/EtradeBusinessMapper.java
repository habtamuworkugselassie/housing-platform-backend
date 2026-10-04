package com.housingplatform.etrade.service;

import com.housingplatform.etrade.client.EtradeRegistrationResponse;
import com.housingplatform.etrade.dto.EtradeBusinessDetails;
import com.housingplatform.etrade.dto.EtradeBusinessDetails.LicenseStatus;
import com.housingplatform.etrade.dto.EtradeBusinessDetails.LicensedBusiness;
import com.housingplatform.etrade.dto.EtradeLegalCondition;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Turns eTrade's wire record into {@link EtradeBusinessDetails}. */
@Component
public class EtradeBusinessMapper {

  /** eTrade's English endpoint formats dates as month/day/year without zero padding. */
  private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("M/d/yyyy");

  private final Clock clock;

  public EtradeBusinessMapper() {
    this(Clock.systemDefaultZone());
  }

  EtradeBusinessMapper(Clock clock) {
    this.clock = clock;
  }

  public EtradeBusinessDetails toDetails(
      EtradeRegistrationResponse src, String sourceUrl, boolean includePhotos) {
    LocalDate today = LocalDate.now(clock);
    List<LicensedBusiness> businesses =
        src.businesses() == null
            ? List.of()
            : src.businesses().stream()
                .filter(Objects::nonNull)
                .map(b -> toBusiness(b, today))
                .toList();
    List<EtradeBusinessDetails.Associate> associates =
        src.associates() == null
            ? List.of()
            : src.associates().stream()
                .filter(Objects::nonNull)
                .map(a -> toAssociate(a, includePhotos))
                .toList();
    return EtradeBusinessDetails.builder()
        .tin(trim(src.tin()))
        .businessName(trim(src.businessName()))
        .businessNameAmharic(trim(src.businessNameAmharic()))
        .legalCondition(EtradeLegalCondition.ofCode(src.legalCondition()))
        .registrationNumber(trim(src.registrationNumber()))
        .registrationDate(parseDate(src.registrationDate()))
        .registrationDateRaw(rawIfUnparseable(src.registrationDate()))
        .paidUpCapital(src.paidUpCapital())
        .associates(associates)
        .businesses(businesses)
        .hasActiveLicense(
            businesses.stream().anyMatch(b -> b.licenseStatus() == LicenseStatus.ACTIVE))
        .fetchedAt(Instant.now(clock))
        .sourceUrl(sourceUrl)
        .build();
  }

  private static EtradeBusinessDetails.Associate toAssociate(
      EtradeRegistrationResponse.Associate a, boolean includePhotos) {
    String photo = null;
    if (includePhotos && a.photo() != null && !a.photo().isBlank()) {
      photo = "data:image/jpeg;base64," + a.photo().trim();
    }
    return EtradeBusinessDetails.Associate.builder()
        .name(trim(a.managerNameEnglish()))
        .nameAmharic(trim(a.managerName()))
        .position(trim(a.position()))
        .mobilePhone(trim(a.mobilePhone()))
        .regularPhone(trim(a.regularPhone()))
        .photoDataUrl(photo)
        .build();
  }

  private static LicensedBusiness toBusiness(
      EtradeRegistrationResponse.Business b, LocalDate today) {
    LocalDate from = parseDate(b.renewedFrom());
    LocalDate to = parseDate(b.renewedTo());
    List<EtradeBusinessDetails.Sector> sectors =
        b.subGroups() == null
            ? List.of()
            : b.subGroups().stream()
                .filter(Objects::nonNull)
                .map(s -> new EtradeBusinessDetails.Sector(s.code(), trim(s.description())))
                .toList();
    return LicensedBusiness.builder()
        .id(trim(b.mainGuid()))
        .tradeName(trim(b.tradeName()))
        .tradeNameAmharic(trim(b.tradeNameAmharic()))
        .licenseNumber(trim(b.licenceNumber()))
        .dateRegistered(parseDate(b.dateRegistered()))
        .dateRegisteredRaw(rawIfUnparseable(b.dateRegistered()))
        .renewalDate(parseDate(b.renewalDate()))
        .renewalDateRaw(rawIfUnparseable(b.renewalDate()))
        .renewedFrom(from)
        .renewedFromRaw(rawIfUnparseable(b.renewedFrom()))
        .renewedTo(to)
        .renewedToRaw(rawIfUnparseable(b.renewedTo()))
        .licenseStatus(licenseStatus(from, to, today))
        .sectors(sectors)
        .build();
  }

  static LicenseStatus licenseStatus(LocalDate from, LocalDate to, LocalDate today) {
    if (to == null) {
      return LicenseStatus.UNKNOWN;
    }
    if (today.isAfter(to)) {
      return LicenseStatus.EXPIRED;
    }
    if (from != null && today.isBefore(from)) {
      return LicenseStatus.NOT_YET_VALID;
    }
    return LicenseStatus.ACTIVE;
  }

  /** Parses eTrade's {@code M/d/yyyy}; also accepts ISO. Returns null when it cannot. */
  static LocalDate parseDate(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    String v = s.trim();
    // Some records carry a time part ("8/30/2024 12:00:00 AM"); keep the date.
    int space = v.indexOf(' ');
    if (space > 0) {
      v = v.substring(0, space);
    }
    try {
      return LocalDate.parse(v, US_DATE);
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return LocalDate.parse(v);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private static String rawIfUnparseable(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    return parseDate(s) == null ? s.trim() : null;
  }

  private static String trim(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}
