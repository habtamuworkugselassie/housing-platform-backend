package com.housingplatform.enquiry.api;

import com.housingplatform.enquiry.dto.ListingEnquiryRequest;
import com.housingplatform.enquiry.dto.ListingEnquiryResponse;
import com.housingplatform.enquiry.service.ListingEnquiryService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enquiries")
@Tag(name = "Enquiries", description = "Listing enquiry forms (public)")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class ListingEnquiryController {

  private final ListingEnquiryService service;

  @PostMapping
  @Operation(
      summary = "Send an enquiry about a property or organization",
      description =
          "Public endpoint, no account needed. Stores the lead, emails a receipt to the sender and the enquiry to the listing's company.")
  public ResponseEntity<ListingEnquiryResponse> submit(
      @Valid @RequestBody ListingEnquiryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(request));
  }
}
