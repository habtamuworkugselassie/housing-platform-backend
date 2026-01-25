package com.housingplatform.admin.service;

import com.housingplatform.admin.dto.AdminStatsResponse;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.SponsorshipApplicationRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.loan.repository.LoanApplicationRepository;
import com.housingplatform.property.repository.BuildingRepository;
import com.housingplatform.property.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {
    
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PropertyRepository propertyRepository;
    private final BuildingRepository buildingRepository;
    private final ConstructionProjectRepository constructionProjectRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final SponsorshipApplicationRepository sponsorshipApplicationRepository;
    
    @Override
    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long totalOrganizations = organizationRepository.count();
        long totalProperties = propertyRepository.count();
        long totalBuildings = buildingRepository.count();
        long totalProjects = constructionProjectRepository.count();
        long totalLoanApplications = loanApplicationRepository.count();
        
        // Count pending approvals (organizations with PENDING_APPROVAL status)
        long pendingApprovals = organizationRepository.findByStatus(Organization.OrganizationStatus.PENDING_APPROVAL).size();
        
        // Count active sponsorships
        long activeSponsorships = sponsorshipApplicationRepository.findAllActiveApplications(LocalDateTime.now()).size();
        
        // Recent activity (simplified - can be enhanced later with audit logs)
        List<AdminStatsResponse.ActivityItem> recentActivity = new ArrayList<>();
        
        return AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalOrganizations(totalOrganizations)
                .totalProperties(totalProperties)
                .totalBuildings(totalBuildings)
                .totalProjects(totalProjects)
                .totalLoanApplications(totalLoanApplications)
                .pendingApprovals(pendingApprovals)
                .activeSponsorships(activeSponsorships)
                .recentActivity(recentActivity)
                .build();
    }
}
