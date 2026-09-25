package com.housingplatform.property.service;

import com.housingplatform.property.domain.PropertyDocument;
import com.housingplatform.property.dto.PropertyDocumentResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/**
 * Official property documents. Sellers (the listing's agent, a super agent of its company, or an
 * admin) upload and manage them; admins verify them; buyers see verified ones only.
 */
public interface PropertyDocumentService {

  record Upload(
      PropertyDocument.DocumentType type,
      String documentNumber,
      String issuingAuthority,
      LocalDate issuedOn,
      String description) {}

  /** A file ready to stream. */
  record DocumentFile(Path path, String fileName, String contentType, long size) {}

  // ---- seller (or admin)

  List<PropertyDocumentResponse> listForManager(UUID propertyId);

  PropertyDocumentResponse upload(UUID propertyId, MultipartFile file, Upload details);

  void delete(UUID propertyId, UUID documentId);

  DocumentFile fileForManager(UUID propertyId, UUID documentId) throws IOException;

  // ---- admin

  Page<PropertyDocumentResponse> adminList(PropertyDocument.DocumentStatus status, Pageable page);

  PropertyDocumentResponse verify(UUID adminUserId, UUID documentId, String note);

  PropertyDocumentResponse reject(UUID adminUserId, UUID documentId, String note);

  DocumentFile fileForAdmin(UUID documentId) throws IOException;

  // ---- buyers (verified documents only)

  List<PropertyDocumentResponse> verified(UUID propertyId);

  DocumentFile verifiedFile(UUID propertyId, UUID documentId) throws IOException;
}
