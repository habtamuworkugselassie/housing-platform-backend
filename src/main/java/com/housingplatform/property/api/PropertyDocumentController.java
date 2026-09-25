package com.housingplatform.property.api;

import com.housingplatform.property.domain.PropertyDocument;
import com.housingplatform.property.dto.PropertyDocumentResponse;
import com.housingplatform.property.service.PropertyDocumentService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** The seller's official documents for a listing (title deed, lease contract, permits). */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/documents")
@Tag(name = "Property documents")
@RequiredArgsConstructor
public class PropertyDocumentController {

  private final PropertyDocumentService documents;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @Operation(summary = "List the listing's documents (seller or admin)")
  public ResponseEntity<List<PropertyDocumentResponse>> list(@PathVariable UUID propertyId) {
    return ResponseEntity.ok(documents.listForManager(propertyId));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @Operation(
      summary = "Upload an official document",
      description = "PDF, JPG or PNG up to 15 MB. It stays PENDING until an admin verifies it.")
  public ResponseEntity<PropertyDocumentResponse> upload(
      @PathVariable UUID propertyId,
      @RequestParam("file") MultipartFile file,
      @RequestParam("documentType") PropertyDocument.DocumentType documentType,
      @RequestParam(required = false) String documentNumber,
      @RequestParam(required = false) String issuingAuthority,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate issuedOn,
      @RequestParam(required = false) String description) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            documents.upload(
                propertyId,
                file,
                new PropertyDocumentService.Upload(
                    documentType, documentNumber, issuingAuthority, issuedOn, description)));
  }

  @DeleteMapping("/{documentId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @Operation(summary = "Remove a pending or rejected document")
  public ResponseEntity<Void> delete(@PathVariable UUID propertyId, @PathVariable UUID documentId) {
    documents.delete(propertyId, documentId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{documentId}/file")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @Operation(summary = "Open a document file (seller or admin)")
  public ResponseEntity<Resource> file(@PathVariable UUID propertyId, @PathVariable UUID documentId)
      throws IOException {
    return DocumentFileResponses.inline(documents.fileForManager(propertyId, documentId));
  }
}
