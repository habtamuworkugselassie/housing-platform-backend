package com.housingplatform.identity.repository;

import com.housingplatform.identity.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
  Optional<User> findByEmail(String email);

  Optional<User> findByPhoneNumber(String phoneNumber);

  boolean existsByEmail(String email);

  boolean existsByPhoneNumber(String phoneNumber);

  @Query("SELECT u FROM User u WHERE u.organization.id = :organizationId ORDER BY u.createdAt ASC")
  List<User> findByOrganizationId(@Param("organizationId") UUID organizationId);

  @Query(
      "SELECT u FROM User u WHERE "
          + "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR "
          + "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
          + "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
          + "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%'))")
  Page<User> searchUsers(@Param("search") String search, Pageable pageable);

  /** Count active users holding a role, excluding one user — used to guard the last super-admin. */
  @Query(
      "SELECT COUNT(u) FROM User u JOIN u.roles r "
          + "WHERE r = :role AND u.status = :status AND u.id <> :excludeId")
  long countByRoleAndStatusExcludingId(
      @Param("role") User.UserRole role,
      @Param("status") User.UserStatus status,
      @Param("excludeId") UUID excludeId);
}
