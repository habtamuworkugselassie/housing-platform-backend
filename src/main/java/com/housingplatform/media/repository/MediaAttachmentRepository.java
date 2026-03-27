package com.housingplatform.media.repository;

import com.housingplatform.media.domain.MediaAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAttachmentRepository extends JpaRepository<MediaAttachment, UUID> {

  @Query("SELECT m FROM MediaAttachment m WHERE m.user.id = :userId ORDER BY m.displayOrder ASC")
  List<MediaAttachment> findByUserIdOrderByDisplayOrderAsc(@Param("userId") UUID userId);

  List<MediaAttachment> findByPropertyIdOrderByDisplayOrderAsc(UUID propertyId);

  List<MediaAttachment> findByOrganizationIdOrderByDisplayOrderAsc(UUID organizationId);

  @Query("SELECT m FROM MediaAttachment m WHERE m.id = :id AND m.user.id = :userId")
  Optional<MediaAttachment> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

  Optional<MediaAttachment> findByIdAndPropertyId(UUID id, UUID propertyId);

  Optional<MediaAttachment> findByIdAndOrganizationId(UUID id, UUID organizationId);

  void deleteByPropertyId(UUID propertyId);

  void deleteByOrganizationId(UUID organizationId);
}
