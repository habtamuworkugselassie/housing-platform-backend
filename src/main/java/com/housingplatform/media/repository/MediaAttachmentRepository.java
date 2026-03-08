package com.housingplatform.media.repository;

import com.housingplatform.media.domain.MediaAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAttachmentRepository extends JpaRepository<MediaAttachment, UUID> {

  List<MediaAttachment> findByUserIdOrderByDisplayOrderAsc(UUID userId);

  List<MediaAttachment> findByPropertyIdOrderByDisplayOrderAsc(UUID propertyId);

  List<MediaAttachment> findByOrganizationIdOrderByDisplayOrderAsc(UUID organizationId);

  Optional<MediaAttachment> findByIdAndPropertyId(UUID id, UUID propertyId);

  Optional<MediaAttachment> findByIdAndOrganizationId(UUID id, UUID organizationId);

  void deleteByPropertyId(UUID propertyId);

  void deleteByOrganizationId(UUID organizationId);
}
