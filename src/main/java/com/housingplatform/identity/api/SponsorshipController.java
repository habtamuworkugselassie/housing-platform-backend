package com.housingplatform.identity.api;

import com.housingplatform.identity.dto.*;
import com.housingplatform.identity.service.OrganizationService;
import com.housingplatform.identity.service.SponsorshipService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sponsorships")
@Tag(name = "Sponsorships", description = "Sponsorship package and application management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class SponsorshipController {
    
    private final SponsorshipService sponsorshipService;
    private final OrganizationService organizationService;
    
    // ========== Sponsorship Package Management (Admin Only) ==========
    
    @PostMapping
    @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
    @AuthActionScope("sponsorships.create")
    @Operation(summary = "Create sponsorship package", description = "Create a new sponsorship package (admin only)")
    public ResponseEntity<SponsorshipResponse> createSponsorship(@Valid @RequestBody CreateSponsorshipRequest request) {
        SponsorshipResponse created = sponsorshipService.createSponsorship(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping
    @Operation(summary = "List all sponsorship packages", description = "Get all available sponsorship packages")
    public ResponseEntity<List<SponsorshipResponse>> getAllSponsorships() {
        List<SponsorshipResponse> sponsorships = sponsorshipService.getAllSponsorships();
        return ResponseEntity.ok(sponsorships);
    }
    
    @GetMapping("/active")
    @Operation(summary = "Get active sponsorship packages", description = "Get all active sponsorship packages")
    public ResponseEntity<List<SponsorshipResponse>> getActiveSponsorships() {
        List<SponsorshipResponse> sponsorships = sponsorshipService.getActiveSponsorships();
        return ResponseEntity.ok(sponsorships);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get sponsorship package by ID", description = "Get sponsorship package details by ID")
    public ResponseEntity<SponsorshipResponse> getSponsorshipById(@PathVariable UUID id) {
        SponsorshipResponse sponsorship = sponsorshipService.getSponsorshipById(id);
        return ResponseEntity.ok(sponsorship);
    }
    
    @PutMapping("/{id}")
    @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
    @AuthActionScope("sponsorships.update")
    @Operation(summary = "Update sponsorship package", description = "Update sponsorship package details (admin only)")
    public ResponseEntity<SponsorshipResponse> updateSponsorship(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSponsorshipRequest request) {
        SponsorshipResponse updated = sponsorshipService.updateSponsorship(id, request);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
    @AuthActionScope("sponsorships.delete")
    @Operation(summary = "Delete sponsorship package", description = "Delete a sponsorship package (admin only)")
    public ResponseEntity<Void> deleteSponsorship(@PathVariable UUID id) {
        sponsorshipService.deleteSponsorship(id);
        return ResponseEntity.noContent().build();
    }
    
    // ========== Sponsorship Application Management ==========
    
    @PostMapping("/applications")
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
    @AuthActionScope("sponsorships.apply")
    @Operation(summary = "Apply for sponsorship", description = "Super agents can apply for sponsorship on behalf of their organization")
    public ResponseEntity<SponsorshipApplicationResponse> applyForSponsorship(
            @Valid @RequestBody SponsorshipApplicationRequest request) {
        // Get organization ID from current user's organization (super agent)
        OrganizationResponse myOrg = organizationService.getMyOrganization();
        UUID organizationId = myOrg.getId();
        SponsorshipApplicationResponse application = sponsorshipService.applyForSponsorship(organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(application);
    }
    
    @GetMapping("/applications")
    @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
    @Operation(summary = "List all applications", description = "Get all sponsorship applications (admin only)")
    public ResponseEntity<List<SponsorshipApplicationResponse>> getAllApplications() {
        List<SponsorshipApplicationResponse> applications = sponsorshipService.getAllApplications();
        return ResponseEntity.ok(applications);
    }
    
    @GetMapping("/applications/pending")
    @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
    @Operation(summary = "Get pending applications", description = "Get all pending sponsorship applications (admin only)")
    public ResponseEntity<List<SponsorshipApplicationResponse>> getPendingApplications() {
        List<SponsorshipApplicationResponse> applications = sponsorshipService.getPendingApplications();
        return ResponseEntity.ok(applications);
    }
    
    @GetMapping("/applications/my-organization")
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
    @Operation(summary = "Get my organization's applications", description = "Get all sponsorship applications for the current user's organization")
    public ResponseEntity<List<SponsorshipApplicationResponse>> getMyOrganizationApplications() {
        OrganizationResponse myOrg = organizationService.getMyOrganization();
        List<SponsorshipApplicationResponse> applications = sponsorshipService.getApplicationsByOrganization(myOrg.getId());
        return ResponseEntity.ok(applications);
    }
    
    @GetMapping("/applications/{id}")
    @Operation(summary = "Get application by ID", description = "Get sponsorship application details by ID")
    public ResponseEntity<SponsorshipApplicationResponse> getApplicationById(@PathVariable UUID id) {
        SponsorshipApplicationResponse application = sponsorshipService.getApplicationById(id);
        return ResponseEntity.ok(application);
    }
    
    @PutMapping("/applications/{id}/approve")
    @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
    @AuthActionScope("sponsorships.approve")
    @Operation(summary = "Approve application", description = "Approve a sponsorship application (admin only)")
    public ResponseEntity<SponsorshipApplicationResponse> approveApplication(
            @PathVariable UUID id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        SponsorshipApplicationResponse approved = sponsorshipService.approveApplication(id, notes);
        return ResponseEntity.ok(approved);
    }
    
    @PutMapping("/applications/{id}/reject")
    @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
    @AuthActionScope("sponsorships.reject")
    @Operation(summary = "Reject application", description = "Reject a sponsorship application (admin only)")
    public ResponseEntity<SponsorshipApplicationResponse> rejectApplication(
            @PathVariable UUID id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        SponsorshipApplicationResponse rejected = sponsorshipService.rejectApplication(id, reason);
        return ResponseEntity.ok(rejected);
    }
    
    @PutMapping("/applications/{id}/cancel")
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
    @AuthActionScope("sponsorships.cancel")
    @Operation(summary = "Cancel application", description = "Cancel a sponsorship application")
    public ResponseEntity<Void> cancelApplication(@PathVariable UUID id) {
        sponsorshipService.cancelApplication(id);
        return ResponseEntity.noContent().build();
    }
}
