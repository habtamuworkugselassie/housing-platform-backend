package com.housingplatform.property.service.impl;

import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.media.service.MediaStorageService;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.domain.PropertyDocument;
import com.housingplatform.property.domain.PropertyDocument.DocumentStatus;
import com.housingplatform.property.dto.PropertyDocumentResponse;
import com.housingplatform.property.repository.PropertyDocumentRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.property.service.PropertyDocumentService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PropertyDocumentServiceImpl implements PropertyDocumentService {

  /** Scans and photos of deeds are small; 15 MB leaves room for multi-page colour PDFs. */
  static final long MAX_BYTES = 15L * 1024 * 1024;

  static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

  /** Under the uploads root but refused by the public uploads route. */
  static final String PRIVATE_DIR = "private/property-documents/";

  private final PropertyDocumentRepository documentRepository;
  private final PropertyRepository propertyRepository;
  private final RealEstateAgentRepository agentRepository;
  private final MediaStorageService storage;

  // ------------------------------------------------------------------ seller

  @Override
  @Transactional(readOnly = true)
  public List<PropertyDocumentResponse> listForManager(UUID propertyId) {
    Property property = requireManaged(propertyId);
    return documentRepository.findByPropertyIdOrderByCreatedAtAsc(property.getId()).stream()
        .map(PropertyDocumentServiceImpl::toResponse)
        .toList();
  }

  @Override
  public PropertyDocumentResponse upload(UUID propertyId, MultipartFile file, Upload details) {
    Property property = requireManaged(propertyId);
    if (file == null || file.isEmpty()) {
      throw new BusinessException("Choose a file to upload");
    }
    if (file.getSize() > MAX_BYTES) {
      throw new BusinessException("The file is larger than 15 MB");
    }
    String contentType = normaliseType(file.getContentType(), file.getOriginalFilename());
    if (!ALLOWED_TYPES.contains(contentType)) {
      throw new BusinessException("Upload a PDF, JPG or PNG file");
    }
    if (details == null || details.type() == null) {
      throw new BusinessException("Choose the document type");
    }
    String sha256 = sha256(file);
    String url = storage.save(file, PRIVATE_DIR + property.getId());
    PropertyDocument doc =
        PropertyDocument.builder()
            .property(property)
            .documentType(details.type().name())
            .documentNumber(trim(details.documentNumber(), 100))
            .issuingAuthority(trim(details.issuingAuthority(), 255))
            .issuedOn(details.issuedOn())
            .description(trim(details.description(), 1000))
            .fileName(safeName(file.getOriginalFilename()))
            .fileUrl(url)
            .contentType(contentType)
            .sizeBytes(file.getSize())
            .sha256(sha256)
            .status(DocumentStatus.PENDING)
            .uploadedByUserId(UserContext.getCurrentUserIdOrNull())
            .build();
    return toResponse(documentRepository.save(doc));
  }

  @Override
  public void delete(UUID propertyId, UUID documentId) {
    Property property = requireManaged(propertyId);
    PropertyDocument doc = requireDocument(property.getId(), documentId);
    // A verified document may already be quoted in signed agreements; only admins remove it.
    if (doc.getStatus() == DocumentStatus.VERIFIED && !UserContext.isAdmin()) {
      throw new BusinessException(
          "A verified document can only be removed by the platform. Upload a new version instead.");
    }
    documentRepository.delete(doc);
    try {
      storage.deleteByUrl(doc.getFileUrl());
    } catch (RuntimeException e) {
      log.warn("Could not delete document file {}: {}", doc.getId(), e.getMessage());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public DocumentFile fileForManager(UUID propertyId, UUID documentId) throws IOException {
    Property property = requireManaged(propertyId);
    return fileOf(requireDocument(property.getId(), documentId));
  }

  // ------------------------------------------------------------------ admin

  @Override
  @Transactional(readOnly = true)
  public Page<PropertyDocumentResponse> adminList(DocumentStatus status, Pageable page) {
    Page<PropertyDocument> docs =
        status != null
            ? documentRepository.findByStatus(status, page)
            : documentRepository.findAllBy(page);
    return docs.map(PropertyDocumentServiceImpl::toResponse);
  }

  @Override
  public PropertyDocumentResponse verify(UUID adminUserId, UUID documentId, String note) {
    return review(adminUserId, documentId, DocumentStatus.VERIFIED, note);
  }

  @Override
  public PropertyDocumentResponse reject(UUID adminUserId, UUID documentId, String note) {
    if (note == null || note.isBlank()) {
      throw new BusinessException("Say why the document is rejected so the seller can fix it");
    }
    return review(adminUserId, documentId, DocumentStatus.REJECTED, note);
  }

  @Override
  @Transactional(readOnly = true)
  public DocumentFile fileForAdmin(UUID documentId) throws IOException {
    return fileOf(
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("PropertyDocument", documentId)));
  }

  // ------------------------------------------------------------------ buyers

  @Override
  @Transactional(readOnly = true)
  public List<PropertyDocumentResponse> verified(UUID propertyId) {
    return documentRepository
        .findByPropertyIdAndStatusOrderByCreatedAtAsc(propertyId, DocumentStatus.VERIFIED)
        .stream()
        .map(PropertyDocumentServiceImpl::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public DocumentFile verifiedFile(UUID propertyId, UUID documentId) throws IOException {
    PropertyDocument doc = requireDocument(propertyId, documentId);
    if (doc.getStatus() != DocumentStatus.VERIFIED) {
      throw new ResourceNotFoundException("PropertyDocument", documentId);
    }
    log.info(
        "Property document {} of property {} opened by user {}",
        documentId,
        propertyId,
        UserContext.getCurrentUserIdOrNull());
    return fileOf(doc);
  }

  // ------------------------------------------------------------------ helpers

  private PropertyDocumentResponse review(
      UUID adminUserId, UUID documentId, DocumentStatus status, String note) {
    PropertyDocument doc =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("PropertyDocument", documentId));
    doc.setStatus(status);
    doc.setReviewNote(trim(note, 2000));
    doc.setReviewedByUserId(adminUserId);
    doc.setReviewedAt(LocalDateTime.now());
    return toResponse(documentRepository.save(doc));
  }

  /**
   * The caller manages the listing: an admin, the listing's agent, or a super agent of the
   * listing's company. A realtor without an agent record manages nothing.
   */
  private Property requireManaged(UUID propertyId) {
    Property property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));
    if (UserContext.isAdmin()) {
      return property;
    }
    UUID userId = UserContext.getCurrentUserIdOrNull();
    RealEstateAgent agent =
        userId != null ? agentRepository.findByUserId(userId).orElse(null) : null;
    boolean ownAgent = agent != null && Objects.equals(property.getAgentId(), agent.getId());
    boolean superAgent =
        agent != null
            && Boolean.TRUE.equals(agent.getIsSuperAgent())
            && agent.getOrganization() != null
            && Objects.equals(agent.getOrganization().getId(), property.getRealEstateCompanyId());
    if (!ownAgent && !superAgent) {
      throw new BusinessException("You can only manage documents of your own listings");
    }
    return property;
  }

  private PropertyDocument requireDocument(UUID propertyId, UUID documentId) {
    return documentRepository
        .findByIdAndPropertyId(documentId, propertyId)
        .orElseThrow(() -> new ResourceNotFoundException("PropertyDocument", documentId));
  }

  private DocumentFile fileOf(PropertyDocument doc) throws IOException {
    Path path = storage.resolveUploadPath(doc.getFileUrl());
    return new DocumentFile(
        path,
        doc.getFileName(),
        doc.getContentType() != null ? doc.getContentType() : "application/octet-stream",
        Files.size(path));
  }

  static PropertyDocumentResponse toResponse(PropertyDocument d) {
    PropertyDocument.DocumentType type =
        PropertyDocument.DocumentType.fromStored(d.getDocumentType());
    Property p = d.getProperty();
    return PropertyDocumentResponse.builder()
        .id(d.getId())
        .propertyId(p != null ? p.getId() : null)
        .propertyTitle(p != null ? p.getTitle() : null)
        .documentType(type)
        .documentTypeLabel(type.label())
        .documentNumber(d.getDocumentNumber())
        .issuingAuthority(d.getIssuingAuthority())
        .issuedOn(d.getIssuedOn())
        .description(d.getDescription())
        .fileName(d.getFileName())
        .contentType(d.getContentType())
        .sizeBytes(d.getSizeBytes())
        .sha256(d.getSha256())
        .status(d.getStatus())
        .reviewNote(d.getReviewNote())
        .reviewedAt(d.getReviewedAt())
        .createdAt(d.getCreatedAt())
        .build();
  }

  /** Browsers sometimes send octet-stream for PDFs; fall back to the extension. */
  static String normaliseType(String contentType, String fileName) {
    String type = contentType != null ? contentType.toLowerCase(Locale.ROOT).trim() : "";
    if (ALLOWED_TYPES.contains(type)) {
      return type;
    }
    String name = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
    if (name.endsWith(".pdf")) return "application/pdf";
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
    if (name.endsWith(".png")) return "image/png";
    return type;
  }

  static String sha256(MultipartFile file) {
    try (InputStream in =
        new DigestInputStream(file.getInputStream(), MessageDigest.getInstance("SHA-256"))) {
      in.transferTo(java.io.OutputStream.nullOutputStream());
      return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new BusinessException("Could not read the uploaded file");
    }
  }

  private static String safeName(String name) {
    if (name == null || name.isBlank()) {
      return "document";
    }
    String base = name.replace('\\', '/');
    base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\r\\n\"]", "");
    return base.length() > 200 ? base.substring(base.length() - 200) : base;
  }

  private static String trim(String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String t = value.trim();
    return t.length() > max ? t.substring(0, max) : t;
  }
}
