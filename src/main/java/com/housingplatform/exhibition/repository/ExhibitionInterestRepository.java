package com.housingplatform.exhibition.repository;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ExhibitionInterestRepository extends JpaRepository<ExhibitionInterest, UUID> {

  @EntityGraph(
      attributePaths = {
        "organization",
        "organization.contact",
        "organization.contact.phones",
        "organization.primaryContact",
        "sponsorship"
      })
  Page<ExhibitionInterest> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Optional<ExhibitionInterest> findByUnsubscribeToken(String unsubscribeToken);

  /**
   * Everyone who could still receive a reminder, oldest first.
   *
   * <p>Ids only, and opt-outs filtered in SQL: the daily job diffs this against the send log and
   * mails a bounded slice of what is left, so pulling whole entities for a list that is mostly
   * discarded would be waste. Whether each of these has *already* had the reminder in question is
   * the send log's question, not this one's.
   */
  @Query(
      "SELECT i.id FROM ExhibitionInterest i WHERE i.unsubscribedAt IS NULL"
          + " ORDER BY i.createdAt ASC")
  List<UUID> findReminderCandidateIds();
}
