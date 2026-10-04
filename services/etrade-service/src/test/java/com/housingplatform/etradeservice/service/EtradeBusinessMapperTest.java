package com.housingplatform.etradeservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.etradeservice.client.EtradeRegistrationResponse;
import com.housingplatform.etradeservice.dto.EtradeBusinessDetails;
import com.housingplatform.etradeservice.dto.EtradeBusinessDetails.LicenseStatus;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class EtradeBusinessMapperTest {

  /** 2026-10-04: inside the fixture's 2026-07-08 .. 2027-07-07 licence period. */
  private static final Clock TODAY =
      Clock.fixed(Instant.parse("2026-10-04T09:00:00Z"), ZoneOffset.UTC);

  private final EtradeBusinessMapper mapper = new EtradeBusinessMapper(TODAY);

  private static EtradeRegistrationResponse fixture() throws IOException {
    return new ObjectMapper()
        .readValue(
            new ClassPathResource("etrade/registration-0081723672.json").getInputStream(),
            EtradeRegistrationResponse.class);
  }

  @Test
  void mapsTheRegistrationAndItsLicensedBusiness() throws IOException {
    EtradeBusinessDetails d =
        mapper.toDetails(
            fixture(), "https://etrade.gov.et/business-license-checker?tin=0081723672", false);

    assertThat(d.tin()).isEqualTo("0081723672");
    assertThat(d.businessName()).isEqualTo("FIREW WONDE BELAY");
    assertThat(d.businessNameAmharic()).isEqualTo("ፍሬው ወንዴ በላይ");
    assertThat(d.legalCondition().code()).isEqualTo("1");
    assertThat(d.legalCondition().name()).isEqualTo("Sole Proprietorship");
    assertThat(d.legalCondition().nameAmharic()).isEqualTo("ግለሰብ ነጋዴ");
    assertThat(d.registrationNumber()).isEqualTo("AA/LK/14/1/3631426/2016");
    assertThat(d.registrationDate()).isEqualTo(LocalDate.of(2022, 12, 1));
    assertThat(d.registrationDateRaw()).isNull();
    assertThat(d.paidUpCapital()).isEqualByComparingTo("500.00");
    assertThat(d.fetchedAt()).isEqualTo(Instant.parse("2026-10-04T09:00:00Z"));
    assertThat(d.sourceUrl()).endsWith("?tin=0081723672");

    assertThat(d.associates()).hasSize(1);
    EtradeBusinessDetails.Associate a = d.associates().get(0);
    assertThat(a.name()).isEqualTo("FIREW WONDE");
    assertThat(a.nameAmharic()).isEqualTo("ፍሬው ወንዴ");
    assertThat(a.photoDataUrl()).as("photos are opt-in").isNull();

    assertThat(d.businesses()).hasSize(1);
    EtradeBusinessDetails.LicensedBusiness b = d.businesses().get(0);
    assertThat(b.id()).isEqualTo("1e18bf2a-6532-4249-b937-65ffbeb4cb54");
    assertThat(b.tradeName()).isEqualTo("FIREW WONDE BELAY");
    assertThat(b.licenseNumber()).isEqualTo("YK/AA/14/672/5990336/2016");
    assertThat(b.dateRegistered()).isEqualTo(LocalDate.of(2024, 8, 30));
    assertThat(b.renewalDate()).isEqualTo(LocalDate.of(2026, 9, 2));
    assertThat(b.renewedFrom()).isEqualTo(LocalDate.of(2026, 7, 8));
    assertThat(b.renewedTo()).isEqualTo(LocalDate.of(2027, 7, 7));
    assertThat(b.licenseStatus()).isEqualTo(LicenseStatus.ACTIVE);
    assertThat(b.sectors()).hasSize(1);
    assertThat(b.sectors().get(0).code()).isEqualTo(62222);
    assertThat(b.sectors().get(0).description())
        .as("upstream pads descriptions with whitespace")
        .isEqualTo("Retail trade in beverage products");
    assertThat(d.hasActiveLicense()).isTrue();
  }

  @Test
  void photosAreIncludedAsDataUrlsOnRequest() throws IOException {
    EtradeBusinessDetails d = mapper.toDetails(fixture(), "x", true);
    assertThat(d.associates().get(0).photoDataUrl()).startsWith("data:image/jpeg;base64,/9j/");
  }

  @Test
  void licenseStatusFollowsTheRenewalWindow() {
    LocalDate today = LocalDate.of(2026, 10, 4);
    assertThat(
            EtradeBusinessMapper.licenseStatus(
                LocalDate.of(2026, 7, 8), LocalDate.of(2027, 7, 7), today))
        .isEqualTo(LicenseStatus.ACTIVE);
    assertThat(
            EtradeBusinessMapper.licenseStatus(
                LocalDate.of(2025, 7, 8), LocalDate.of(2026, 7, 7), today))
        .isEqualTo(LicenseStatus.EXPIRED);
    assertThat(
            EtradeBusinessMapper.licenseStatus(
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31), today))
        .isEqualTo(LicenseStatus.NOT_YET_VALID);
    assertThat(EtradeBusinessMapper.licenseStatus(null, null, today))
        .isEqualTo(LicenseStatus.UNKNOWN);
    assertThat(EtradeBusinessMapper.licenseStatus(null, today, today))
        .as("last day of validity is still valid")
        .isEqualTo(LicenseStatus.ACTIVE);
  }

  @Test
  void unparseableDatesAreKeptVerbatim() {
    // The Amharic endpoint returns Ethiopian-calendar dates such as 22/3/2015.
    EtradeRegistrationResponse src =
        new EtradeRegistrationResponse(
            "0081723672",
            "99",
            "AA/1",
            "22/3/2015",
            "X",
            null,
            null,
            null,
            List.of(
                new EtradeRegistrationResponse.Business(
                    "g",
                    "0081723672",
                    "8/30/2024 12:00:00 AM",
                    null,
                    "X",
                    "L/1",
                    null,
                    null,
                    "30/12/2018",
                    null)));
    EtradeBusinessDetails d = mapper.toDetails(src, "x", false);
    assertThat(d.registrationDate()).isNull();
    assertThat(d.registrationDateRaw()).isEqualTo("22/3/2015");
    assertThat(d.legalCondition().code()).isEqualTo("99");
    assertThat(d.legalCondition().name()).isNull();
    assertThat(d.businesses().get(0).dateRegistered()).isEqualTo(LocalDate.of(2024, 8, 30));
    assertThat(d.businesses().get(0).renewedTo()).isNull();
    assertThat(d.businesses().get(0).renewedToRaw()).isEqualTo("30/12/2018");
    assertThat(d.businesses().get(0).licenseStatus()).isEqualTo(LicenseStatus.UNKNOWN);
    assertThat(d.hasActiveLicense()).isFalse();
  }

  @Test
  void toleratesNullCollections() {
    EtradeRegistrationResponse src =
        new EtradeRegistrationResponse("0000000000", "1", null, null, "N", null, null, null, null);
    EtradeBusinessDetails d = mapper.toDetails(src, "x", false);
    assertThat(d.associates()).isEmpty();
    assertThat(d.businesses()).isEmpty();
    assertThat(d.hasActiveLicense()).isFalse();
  }
}
