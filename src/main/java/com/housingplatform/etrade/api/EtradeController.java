package com.housingplatform.etrade.api;

import com.housingplatform.etrade.dto.EtradeBusinessDetails;
import com.housingplatform.etrade.dto.EtradeOrganizationVerification;
import com.housingplatform.etrade.service.EtradeBusinessService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/etrade")
@Tag(
    name = "eTrade",
    description =
        "Ministry of Trade eTrade portal integration: business registration and licence details"
            + " by TIN")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class EtradeController {

  private final EtradeBusinessService etradeBusinessService;

  @GetMapping("/businesses/{tin}")
  @Operation(
      summary = "Look up a business on eTrade by TIN",
      description =
          "Returns the trader's registration (name, legal form, registration number, capital,"
              + " managers) and every business licence under the TIN, as published on"
              + " https://etrade.gov.et/business-license-checker. 400 for a malformed TIN, 404 when"
              + " eTrade has no record, 502 when eTrade cannot be reached. Any signed-in user.")
  public ResponseEntity<EtradeBusinessDetails> getBusinessByTin(
      @Parameter(description = "10-digit Tax Identification Number", example = "0081723672")
          @PathVariable
          String tin,
      @Parameter(description = "Include managers' photos as base64 data URLs (large)")
          @RequestParam(defaultValue = "false")
          boolean includePhotos) {
    return ResponseEntity.ok(etradeBusinessService.getBusinessDetails(tin, includePhotos));
  }

  @GetMapping("/organizations/{organizationId}/verification")
  @Operation(
      summary = "Check an organization against eTrade",
      description =
          "Looks up the organization's declared TIN on eTrade and reports whether its name, licence"
              + " number and business registration number match the public record and whether a"
              + " licence is currently valid. Admin, or the organization's own primary contact /"
              + " members. Nothing is stored; use the document-review endpoint to record the"
              + " outcome.")
  public ResponseEntity<EtradeOrganizationVerification> verifyOrganization(
      @PathVariable UUID organizationId) {
    return ResponseEntity.ok(etradeBusinessService.verifyOrganization(organizationId));
  }
}
