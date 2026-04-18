package com.housingplatform.publicsupport.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupportRagIndexEventListener {

  private final SupportRagIndexingService indexingService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onOrganization(SupportRagIndexEvents.RagIndexOrganizationEvent e) {
    if (!indexingService.isEnabled()) {
      return;
    }
    try {
      indexingService.indexOrganization(e.organizationId());
    } catch (Exception ex) {
      log.warn("RAG index organization failed for {}: {}", e.organizationId(), ex.getMessage());
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProperty(SupportRagIndexEvents.RagIndexPropertyEvent e) {
    if (!indexingService.isEnabled()) {
      return;
    }
    try {
      indexingService.indexProperty(e.propertyId());
    } catch (Exception ex) {
      log.warn("RAG index property failed for {}: {}", e.propertyId(), ex.getMessage());
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSponsorship(SupportRagIndexEvents.RagIndexSponsorshipEvent e) {
    if (!indexingService.isEnabled()) {
      return;
    }
    try {
      indexingService.indexSponsorship(e.sponsorshipId());
    } catch (Exception ex) {
      log.warn("RAG index sponsorship failed for {}: {}", e.sponsorshipId(), ex.getMessage());
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDelete(SupportRagIndexEvents.RagDeleteRagSourceEvent e) {
    if (!indexingService.isEnabled()) {
      return;
    }
    try {
      indexingService.delete(e.sourceType(), e.sourceId());
    } catch (Exception ex) {
      log.warn("RAG delete failed for {} {}: {}", e.sourceType(), e.sourceId(), ex.getMessage());
    }
  }
}
