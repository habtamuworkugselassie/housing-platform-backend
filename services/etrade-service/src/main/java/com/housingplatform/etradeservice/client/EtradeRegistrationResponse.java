package com.housingplatform.etradeservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Wire format of {@code GET /api/Registration/GetRegistrationInfoByTin/{tin}/{lang}} on
 * etrade.gov.et, kept 1:1 with the upstream PascalCase names (typos included). Mapped to {@link
 * com.housingplatform.etradeservice.dto.EtradeBusinessDetails} before it leaves the module.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EtradeRegistrationResponse(
    @JsonProperty("Tin") String tin,
    @JsonProperty("LegalCondtion") String legalCondition,
    @JsonProperty("RegNo") String registrationNumber,
    @JsonProperty("RegDate") String registrationDate,
    @JsonProperty("BusinessName") String businessName,
    @JsonProperty("BusinessNameAmh") String businessNameAmharic,
    @JsonProperty("PaidUpCapital") BigDecimal paidUpCapital,
    @JsonProperty("AssociateShortInfos") List<Associate> associates,
    @JsonProperty("Businesses") List<Business> businesses) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Associate(
      @JsonProperty("Position") String position,
      @JsonProperty("ManagerName") String managerName,
      @JsonProperty("ManagerNameEng") String managerNameEnglish,
      @JsonProperty("Photo") String photo,
      @JsonProperty("MobilePhone") String mobilePhone,
      @JsonProperty("RegularPhone") String regularPhone) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Business(
      @JsonProperty("MainGuid") String mainGuid,
      @JsonProperty("OwnerTIN") String ownerTin,
      @JsonProperty("DateRegistered") String dateRegistered,
      @JsonProperty("TradeNameAmh") String tradeNameAmharic,
      @JsonProperty("TradesName") String tradeName,
      @JsonProperty("LicenceNumber") String licenceNumber,
      @JsonProperty("RenewalDate") String renewalDate,
      @JsonProperty("RenewedFrom") String renewedFrom,
      @JsonProperty("RenewedTo") String renewedTo,
      @JsonProperty("SubGroups") List<SubGroup> subGroups) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SubGroup(
      @JsonProperty("Code") Integer code, @JsonProperty("Description") String description) {}
}
