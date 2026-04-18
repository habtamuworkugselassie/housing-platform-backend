package com.housingplatform.property.repository;

import com.housingplatform.property.domain.Review;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
  List<Review> findByPropertyId(UUID propertyId);

  List<Review> findByOrganizationId(UUID organizationId);

  @org.springframework.data.jpa.repository.Query(
      "SELECT AVG(r.rating) FROM Review r WHERE r.property.id = :propertyId")
  Double getAverageRatingForProperty(
      @org.springframework.data.repository.query.Param("propertyId") UUID propertyId);

  @org.springframework.data.jpa.repository.Query(
      "SELECT COUNT(r) FROM Review r WHERE r.property.id = :propertyId")
  Integer getReviewCountForProperty(
      @org.springframework.data.repository.query.Param("propertyId") UUID propertyId);

  @org.springframework.data.jpa.repository.Query(
      "SELECT AVG(r.rating) FROM Review r WHERE r.organization.id = :organizationId")
  Double getAverageRatingForOrganization(
      @org.springframework.data.repository.query.Param("organizationId") UUID organizationId);

  @org.springframework.data.jpa.repository.Query(
      "SELECT COUNT(r) FROM Review r WHERE r.organization.id = :organizationId")
  Integer getReviewCountForOrganization(
      @org.springframework.data.repository.query.Param("organizationId") UUID organizationId);

  /**
   * Batch review aggregates for organizations (public marketplace / support RAG). One row per org
   * with at least one review.
   */
  @Query(
      "SELECT r.organization.id, AVG(r.rating), COUNT(r) FROM Review r "
          + "WHERE r.organization IS NOT NULL AND r.organization.id IN :organizationIds "
          + "GROUP BY r.organization.id")
  List<Object[]> aggregateReviewStatsByOrganizationIds(
      @Param("organizationIds") List<UUID> organizationIds);

  @Query(
      "SELECT r.property.id, AVG(r.rating), COUNT(r) FROM Review r "
          + "WHERE r.property IS NOT NULL AND r.property.id IN :propertyIds "
          + "GROUP BY r.property.id")
  List<Object[]> aggregateReviewStatsByPropertyIds(@Param("propertyIds") List<UUID> propertyIds);
}
