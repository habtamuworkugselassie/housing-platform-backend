package com.housingplatform.admin.api;

import com.housingplatform.enquiry.domain.ListingEnquiry;
import com.housingplatform.enquiry.dto.ListingEnquiryResponse;
import com.housingplatform.enquiry.dto.UpdateListingEnquiryStatusRequest;
import com.housingplatform.enquiry.service.ListingEnquiryService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Admin follow-up of listing enquiries (property / organization contact forms). */
@RestController
@RequestMapping("/api/v1/admin/enquiries")
@Tag(name = "Admin - Enquiries", description = "Listing enquiry follow-up")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class AdminListingEnquiryController {

  private final ListingEnquiryService service;

  @GetMapping
  @Operation(summary = "List enquiries", description = "Newest first; optionally filter by status.")
  public ResponseEntity<Page<ListingEnquiryResponse>> list(
      @RequestParam(value = "status", required = false) ListingEnquiry.Status status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(service.adminList(status, pageable));
  }

  @GetMapping("/count")
  @Operation(summary = "Count of enquiries not yet contacted")
  public ResponseEntity<Map<String, Long>> countNew() {
    return ResponseEntity.ok(Map.of("newCount", service.countNew()));
  }

  @PutMapping("/{id}/status")
  @Operation(summary = "Update enquiry status (NEW, CONTACTED, CLOSED)")
  public ResponseEntity<ListingEnquiryResponse> updateStatus(
      @PathVariable UUID id, @Valid @RequestBody UpdateListingEnquiryStatusRequest body) {
    return ResponseEntity.ok(service.updateStatus(id, body.getStatus()));
  }
}
