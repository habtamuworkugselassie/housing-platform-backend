package com.housingplatform.exhibition.repository;

import com.housingplatform.exhibition.domain.ExhibitionEmailKind;
import com.housingplatform.exhibition.domain.ExhibitionInterestEmail;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExhibitionInterestEmailRepository
    extends JpaRepository<ExhibitionInterestEmail, UUID> {

  Optional<ExhibitionInterestEmail> findByInterestIdAndKind(
      UUID interestId, ExhibitionEmailKind kind);

  /**
   * Ids of everyone this mail is already settled for — sent, or suppressed and not to be retried.
   *
   * <p>Fetched in one query and diffed against the candidate list, rather than asking per
   * registrant inside the loop: the daily job would otherwise issue one SELECT per lead. A FAILED
   * row is deliberately absent from the result, which is what makes a failure retryable tomorrow.
   */
  default Set<UUID> findSettledInterestIds(ExhibitionEmailKind kind) {
    return findInterestIdsByKindAndStatusNot(kind, ExhibitionInterestEmail.Status.FAILED);
  }

  /**
   * The status to exclude is passed as a parameter rather than written into the query, because HQL
   * cannot resolve a nested enum constant by its qualified name. {@link #findSettledInterestIds} is
   * the method to call.
   */
  @Query(
      "SELECT e.interest.id FROM ExhibitionInterestEmail e"
          + " WHERE e.kind = :kind AND e.status <> :excludedStatus")
  Set<UUID> findInterestIdsByKindAndStatusNot(
      @Param("kind") ExhibitionEmailKind kind,
      @Param("excludedStatus") ExhibitionInterestEmail.Status excludedStatus);
}
