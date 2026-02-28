package com.housingplatform.identity.repository;

import com.housingplatform.identity.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenAndUsedAtIsNullAndExpiresAtAfter(
      String token, Instant now);

  void deleteByUserId(UUID userId);

  @Modifying
  @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
  int deleteExpiredTokens(@Param("now") Instant now);
}
