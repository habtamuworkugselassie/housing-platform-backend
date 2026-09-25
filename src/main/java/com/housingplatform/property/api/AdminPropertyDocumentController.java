package com.housingplatform.property.api;

import com.housingplatform.property.domain.PropertyDocument;
import com.housingplatform.property.dto.PropertyDocumentResponse;
import com.housingplatform.property.service.PropertyDocumentService;
import com.housingplatform.shared.security.UserContext;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Admin review of sellers' property documents. */
@RestController
@RequestMapping("/api/v1/admin/property-documents")
@Tag(name = "Admin - Property documents")
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
@RequiredArgsConstructor
public class AdminPropertyDocumentController {

  private static final int MAX_PAGE_SIZE = 200;

  private final PropertyDocumentService documents;

  public record ReviewRequest(String note) {}

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "List documents, optionally by status (PENDING first to review)")
  public ResponseEntity<Page<PropertyDocumentResponse>> list(
      @RequestParam(required = false) PropertyDocument.DocumentStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return ResponseEntity.ok(
        documents.adminList(
            status,
            PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "createdAt"))));
  }

  @GetMapping("/{documentId}/file")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Open a document file")
  public ResponseEntity<Resource> file(@PathVariable UUID documentId) throws IOException {
    return DocumentFileResponses.inline(documents.fileForAdmin(documentId));
  }

  @PostMapping("/{documentId}/verify")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Mark a document verified; it is then listed in new agreements")
  public ResponseEntity<PropertyDocumentResponse> verify(
      @PathVariable UUID documentId, @RequestBody(required = false) ReviewRequest request) {
    return ResponseEntity.ok(
        documents.verify(
            UserContext.getCurrentUserId(), documentId, request != null ? request.note() : null));
  }

  @PostMapping("/{documentId}/reject")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Reject a document with a reason for the seller")
  public ResponseEntity<PropertyDocumentResponse> reject(
      @PathVariable UUID documentId, @RequestBody ReviewRequest request) {
    return ResponseEntity.ok(
        documents.reject(
            UserContext.getCurrentUserId(), documentId, request != null ? request.note() : null));
  }
}
