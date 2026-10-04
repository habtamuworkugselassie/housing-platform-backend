package com.housingplatform.etrade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.etrade.EtradeProperties;
import com.housingplatform.etrade.client.EtradeRegistrationResponse;
import com.housingplatform.etrade.dto.EtradeBusinessDetails;
import com.housingplatform.etrade.dto.EtradeBusinessDetails.LicenseStatus;
import com.housingplatform.etrade.dto.EtradeOrganizationVerification;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EtradeBusinessServiceTest {

  private EtradeRegistrationLookup lookup;
  private EtradeBusinessMapper mapper;
  private EtradeBusinessService service;

  @BeforeEach
  void setUp() {
    lookup = mock(EtradeRegistrationLookup.class);
    mapper = mock(EtradeBusinessMapper.class);
    service =
        new EtradeBusinessService(
            new EtradeProperties(), lookup, mapper, mock(OrganizationRepository.class));
  }

  @Test
  void normalizesSeparatorsAndRejectsAnythingButTenDigits() {
    assertThat(EtradeBusinessService.normalizeTin(" 0081-723 672 ")).isEqualTo("0081723672");
    assertThat(EtradeBusinessService.normalizeTin("0081723672")).isEqualTo("0081723672");
    assertThatThrownBy(() -> EtradeBusinessService.normalizeTin("123"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("10 digits");
    assertThatThrownBy(() -> EtradeBusinessService.normalizeTin("00817236720"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> EtradeBusinessService.normalizeTin("abcdefghij"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> EtradeBusinessService.normalizeTin(null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void aMalformedTinNeverReachesEtrade() {
    assertThatThrownBy(() -> service.getBusinessDetails("12", false))
        .isInstanceOf(BusinessException.class);
    verify(lookup, never()).fetch(any());
  }

  @Test
  void unknownTinIs404AndCarriesTheNormalizedTin() {
    when(lookup.fetch("0081723672")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getBusinessDetails("0081 723 672", false))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("0081723672");
    assertThat(service.findBusinessDetails("0081723672", false)).isEmpty();
  }

  @Test
  void mapsTheRecordWithThePublicCheckerUrlAsSource() {
    EtradeRegistrationResponse raw =
        new EtradeRegistrationResponse("0081723672", "1", null, null, "N", null, null, null, null);
    EtradeBusinessDetails mapped = EtradeBusinessDetails.builder().tin("0081723672").build();
    when(lookup.fetch("0081723672")).thenReturn(Optional.of(raw));
    when(mapper.toDetails(
            eq(raw), eq("https://etrade.gov.et/business-license-checker?tin=0081723672"), eq(true)))
        .thenReturn(mapped);

    assertThat(service.getBusinessDetails("0081723672", true)).isSameAs(mapped);
    verify(mapper, never()).toDetails(any(), any(), eq(false));
    verify(mapper).toDetails(any(), any(), anyBoolean());
  }

  @Test
  void comparesDeclaredNumbersAndNamesLeniently() {
    Organization org =
        Organization.builder()
            .name("  firew wonde   belay ")
            .licenseNumber("yk/aa/14/672/5990336/2016")
            .businessRegistrationNumber("AA/LK/14/1/3631426/2016")
            .build();
    EtradeBusinessDetails details =
        EtradeBusinessDetails.builder()
            .businessName("FIREW WONDE BELAY")
            .registrationNumber("AA/LK/14/1/3631426/2016")
            .businesses(
                List.of(
                    EtradeBusinessDetails.LicensedBusiness.builder()
                        .tradeName("Something Else")
                        .licenseNumber("YK/AA/14/672/5990336/2016")
                        .licenseStatus(LicenseStatus.EXPIRED)
                        .build()))
            .build();

    EtradeOrganizationVerification.Checks checks = EtradeBusinessService.compare(org, details);

    assertThat(checks.nameMatches()).isTrue();
    assertThat(checks.licenseNumberMatches()).isTrue();
    assertThat(checks.registrationNumberMatches()).isTrue();
    assertThat(checks.licenseActive()).isFalse();
  }

  @Test
  void checksAreNullWhenTheOrganizationDeclaredNothingToCompare() {
    Organization org = Organization.builder().name("Acme PLC").build();
    EtradeBusinessDetails details =
        EtradeBusinessDetails.builder()
            .businessName("ACME TRADING")
            .businesses(
                List.of(
                    EtradeBusinessDetails.LicensedBusiness.builder()
                        .tradeName("Acme PLC")
                        .licenseNumber("L/1")
                        .licenseStatus(LicenseStatus.ACTIVE)
                        .build()))
            .build();

    EtradeOrganizationVerification.Checks checks = EtradeBusinessService.compare(org, details);

    assertThat(checks.nameMatches()).as("trade names count as a match").isTrue();
    assertThat(checks.licenseNumberMatches()).isNull();
    assertThat(checks.registrationNumberMatches()).isNull();
    assertThat(checks.licenseActive()).isTrue();
  }
}
