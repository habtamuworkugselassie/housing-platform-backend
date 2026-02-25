package com.housingplatform.banking.repository;

import com.housingplatform.banking.domain.FinancingOffer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancingOfferRepository extends JpaRepository<FinancingOffer, UUID> {
  List<FinancingOffer> findByBankId(UUID bankId);

  List<FinancingOffer> findByPropertyId(UUID propertyId);

  List<FinancingOffer> findByBuildingId(UUID buildingId);

  List<FinancingOffer> findByProjectId(UUID projectId);

  List<FinancingOffer> findByStatus(FinancingOffer.FinancingOfferStatus status);

  List<FinancingOffer> findByPropertyIdAndStatus(
      UUID propertyId, FinancingOffer.FinancingOfferStatus status);

  List<FinancingOffer> findByBuildingIdAndStatus(
      UUID buildingId, FinancingOffer.FinancingOfferStatus status);

  List<FinancingOffer> findByCreditProductId(UUID creditProductId);

  List<FinancingOffer> findByCreditProductIdAndStatus(
      UUID creditProductId, FinancingOffer.FinancingOfferStatus status);
}
