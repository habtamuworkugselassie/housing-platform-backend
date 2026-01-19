package com.housingplatform.banking.api;

import com.housingplatform.banking.dto.FinancingOfferRequest;
import com.housingplatform.banking.dto.FinancingOfferResponse;
import com.housingplatform.banking.service.FinancingOfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/banks/{bankId}/financing-offers")
@Tag(name = "Financing Offers", description = "Property-linked financing offer management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class FinancingOfferController {
    
    private final FinancingOfferService financingOfferService;
    
    @PostMapping
    @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
    @AuthActionScope("financing-offers.create")
    @Operation(summary = "Create financing offer", description = "Create a new financing offer linked to a property or project")
    public ResponseEntity<FinancingOfferResponse> createFinancingOffer(
            @PathVariable UUID bankId,
            @Valid @RequestBody FinancingOfferRequest offerRequest) {
        FinancingOfferResponse created = financingOfferService.createFinancingOffer(bankId, offerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping
    @Operation(summary = "List financing offers", description = "Retrieve all financing offers for a bank")
    public ResponseEntity<List<FinancingOfferResponse>> getFinancingOffers(@PathVariable UUID bankId) {
        List<FinancingOfferResponse> offers = financingOfferService.getFinancingOffersByBankId(bankId);
        return ResponseEntity.ok(offers);
    }
    
    @GetMapping("/{offerId}")
    @Operation(summary = "Get financing offer", description = "Retrieve a specific financing offer")
    public ResponseEntity<FinancingOfferResponse> getFinancingOffer(
            @PathVariable UUID bankId,
            @PathVariable UUID offerId) {
        FinancingOfferResponse offer = financingOfferService.getFinancingOfferById(offerId);
        return ResponseEntity.ok(offer);
    }
    
    @GetMapping("/properties/{propertyId}")
    @Operation(summary = "Get offers for property", description = "Retrieve all financing offers for a specific property")
    public ResponseEntity<List<FinancingOfferResponse>> getOffersForProperty(
            @PathVariable UUID bankId,
            @PathVariable UUID propertyId) {
        List<FinancingOfferResponse> offers = financingOfferService.getFinancingOffersByPropertyId(propertyId);
        return ResponseEntity.ok(offers);
    }
    
    @PutMapping("/{offerId}")
    @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
    @AuthActionScope("financing-offers.update")
    @Operation(summary = "Update financing offer", description = "Update an existing financing offer")
    public ResponseEntity<FinancingOfferResponse> updateFinancingOffer(
            @PathVariable UUID bankId,
            @PathVariable UUID offerId,
            @Valid @RequestBody FinancingOfferRequest offerRequest) {
        FinancingOfferResponse updated = financingOfferService.updateFinancingOffer(bankId, offerId, offerRequest);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{offerId}")
    @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
    @AuthActionScope("financing-offers.delete")
    @Operation(summary = "Delete financing offer", description = "Delete a financing offer")
    public ResponseEntity<Void> deleteFinancingOffer(
            @PathVariable UUID bankId,
            @PathVariable UUID offerId) {
        financingOfferService.deleteFinancingOffer(bankId, offerId);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/link-to-property")
    @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
    @AuthActionScope("financing-offers.create")
    @Operation(summary = "Link credit product to property", description = "Quickly link a credit product to a property, creating an active financing offer")
    public ResponseEntity<FinancingOfferResponse> linkToProperty(
            @PathVariable UUID bankId,
            @RequestParam UUID creditProductId,
            @RequestParam UUID propertyId) {
        FinancingOfferResponse offer = financingOfferService.linkCreditProductToProperty(bankId, creditProductId, propertyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(offer);
    }
    
    @PostMapping("/link-to-building")
    @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
    @AuthActionScope("financing-offers.create")
    @Operation(summary = "Link credit product to building", description = "Quickly link a credit product to a building, creating an active financing offer")
    public ResponseEntity<FinancingOfferResponse> linkToBuilding(
            @PathVariable UUID bankId,
            @RequestParam UUID creditProductId,
            @RequestParam UUID buildingId) {
        FinancingOfferResponse offer = financingOfferService.linkCreditProductToBuilding(bankId, creditProductId, buildingId);
        return ResponseEntity.status(HttpStatus.CREATED).body(offer);
    }
    
    @GetMapping("/buildings/{buildingId}")
    @Operation(summary = "Get offers for building", description = "Retrieve all financing offers for a specific building")
    public ResponseEntity<List<FinancingOfferResponse>> getOffersForBuilding(
            @PathVariable UUID bankId,
            @PathVariable UUID buildingId) {
        List<FinancingOfferResponse> offers = financingOfferService.getFinancingOffersByBuildingId(buildingId);
        return ResponseEntity.ok(offers);
    }
}
