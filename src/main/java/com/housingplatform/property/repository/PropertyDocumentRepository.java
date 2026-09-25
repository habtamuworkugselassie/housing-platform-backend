package com.housingplatform.property.repository;

import com.housingplatform.property.domain.PropertyDocument;
import com.housingplatform.property.domain.PropertyDocument.DocumentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyDocumentRepository extends JpaRepository<PropertyDocument, UUID> {

  List<PropertyDocument> findByPropertyIdOrderByCreatedAtAsc(UUID propertyId);

  List<PropertyDocument> findByPropertyIdAndStatusOrderByCreatedAtAsc(
      UUID propertyId, DocumentStatus status);

  Optional<PropertyDocument> findByIdAndPropertyId(UUID id, UUID propertyId);

  @EntityGraph(attributePaths = "property")
  Page<PropertyDocument> findByStatus(DocumentStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "property")
  Page<PropertyDocument> findAllBy(Pageable pageable);
}
