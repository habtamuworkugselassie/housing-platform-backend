package com.housingplatform.etradeservice.api;

import com.housingplatform.etradeservice.dto.EtradeBusinessDetails;
import com.housingplatform.etradeservice.service.EtradeBusinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses")
@Tag(name = "Businesses", description = "eTrade business registration and licences by TIN")
@RequiredArgsConstructor
public class BusinessController {

  private final EtradeBusinessService service;

  @GetMapping("/{tin}")
  @Operation(
      summary = "Look up a business on eTrade by TIN",
      description =
          "Registration (name, legal form, registration number, capital, managers) and every"
              + " business licence under the TIN. 400 malformed TIN, 404 unknown TIN, 502 eTrade"
              + " unreachable. Requires X-API-Key when keys are configured.")
  public EtradeBusinessDetails getByTin(
      @Parameter(description = "10-digit Tax Identification Number", example = "0081723672")
          @PathVariable
          String tin,
      @Parameter(description = "Include managers' photos as base64 data URLs (large)")
          @RequestParam(defaultValue = "false")
          boolean includePhotos) {
    return service.getBusinessDetails(tin, includePhotos);
  }
}
