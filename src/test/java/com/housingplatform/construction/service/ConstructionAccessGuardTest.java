package com.housingplatform.construction.service;

import static com.housingplatform.construction.ConstructionTestAuth.signIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.housingplatform.construction.ConstructionTestAuth;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.domain.Material;
import com.housingplatform.construction.domain.MaterialInventory;
import com.housingplatform.construction.domain.MaterialOrder;
import com.housingplatform.construction.domain.ProjectTeamMember;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.construction.repository.ProjectTeamMemberRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConstructionAccessGuardTest {

  @Mock private ConstructionProjectRepository projectRepository;
  @Mock private ProjectTeamMemberRepository teamMemberRepository;
  @Mock private PropertyRepository propertyRepository;

  private ConstructionAccessGuard guard;

  private final UUID ownerOrg = UUID.randomUUID();
  private final UUID otherOrg = UUID.randomUUID();
  private final UUID supplierOrg = UUID.randomUUID();
  private final UUID managerId = UUID.randomUUID();
  private ConstructionProject project;

  @BeforeEach
  void setUp() {
    guard =
        new ConstructionAccessGuard(projectRepository, teamMemberRepository, propertyRepository);
    project =
        ConstructionProject.builder()
            .id(UUID.randomUUID())
            .realEstateCompanyId(ownerOrg)
            .projectManagerId(managerId)
            .build();
    when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
  }

  @AfterEach
  void tearDown() {
    ConstructionTestAuth.clear();
  }

  @Test
  void owningCompanyCanAccessProject() {
    signIn(UUID.randomUUID(), ownerOrg, "realtor");
    assertThat(guard.canAccessProject(project)).isTrue();
    assertThat(guard.requireProject(project.getId())).isSameAs(project);
  }

  @Test
  void otherCompanyIsDeniedAsNotFound() {
    signIn(UUID.randomUUID(), otherOrg, "realtor");
    assertThat(guard.canAccessProject(project)).isFalse();
    assertThatThrownBy(() -> guard.requireProject(project.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void buyerWithoutOrganizationIsDenied() {
    signIn(UUID.randomUUID(), null, "buyer");
    assertThat(guard.canAccessProject(project)).isFalse();
  }

  @Test
  void adminAndSuperAdminCanAccessAnyProject() {
    signIn(UUID.randomUUID(), otherOrg, "admin");
    assertThat(guard.canAccessProject(project)).isTrue();
    signIn(UUID.randomUUID(), null, "super_admin");
    assertThat(guard.canAccessProject(project)).isTrue();
  }

  @Test
  void projectManagerFromAnotherOrganizationCanAccess() {
    signIn(managerId, otherOrg, "realtor");
    assertThat(guard.canAccessProject(project)).isTrue();
  }

  @Test
  void activeTeamMemberCanAccessButRemovedMemberCannot() {
    UUID memberId = UUID.randomUUID();
    ProjectTeamMember member =
        ProjectTeamMember.builder()
            .project(project)
            .userId(memberId)
            .status(ProjectTeamMember.MemberStatus.ACTIVE)
            .build();
    when(teamMemberRepository.findByProjectIdAndUserId(project.getId(), memberId))
        .thenReturn(Optional.of(member));
    signIn(memberId, null, "buyer");
    assertThat(guard.canAccessProject(project)).isTrue();

    member.setStatus(ProjectTeamMember.MemberStatus.REMOVED);
    assertThat(guard.canAccessProject(project)).isFalse();
  }

  @Test
  void unauthenticatedCallerIsDenied() {
    ConstructionTestAuth.clear();
    assertThat(guard.isAdmin()).isFalse();
    assertThat(guard.canAccessProject(project)).isFalse();
  }

  @Test
  void supplierSeesOnlyOrdersPlacedWithIt() {
    MaterialOrder own = MaterialOrder.builder().project(project).supplierId(supplierOrg).build();
    MaterialOrder foreign =
        MaterialOrder.builder().project(project).supplierId(UUID.randomUUID()).build();
    signIn(UUID.randomUUID(), supplierOrg, "supplier");

    assertThat(guard.canAccessOrder(own)).isTrue();
    assertThat(guard.isOrderSupplier(own)).isTrue();
    assertThat(guard.isOrderBuyer(own)).isFalse();
    assertThat(guard.canAccessOrder(foreign)).isFalse();
  }

  @Test
  void projectlessOrderIsVisibleToTheUserWhoPlacedIt() {
    UUID buyer = UUID.randomUUID();
    MaterialOrder order = MaterialOrder.builder().orderedBy(buyer).supplierId(supplierOrg).build();
    signIn(buyer, null, "buyer");
    assertThat(guard.canAccessOrder(order)).isTrue();
    signIn(UUID.randomUUID(), otherOrg, "realtor");
    assertThat(guard.canAccessOrder(order)).isFalse();
  }

  @Test
  void warehouseInventoryIsAdminOnly() {
    MaterialInventory warehouse = MaterialInventory.builder().build();
    MaterialInventory onProject = MaterialInventory.builder().projectId(project.getId()).build();
    signIn(UUID.randomUUID(), ownerOrg, "realtor");
    assertThat(guard.canAccessInventory(warehouse)).isFalse();
    assertThat(guard.canAccessInventory(onProject)).isTrue();
    signIn(UUID.randomUUID(), null, "admin");
    assertThat(guard.canAccessInventory(warehouse)).isTrue();
  }

  @Test
  void onlyOwningSupplierCanManageMaterial() {
    Material material = Material.builder().id(UUID.randomUUID()).supplierId(supplierOrg).build();
    signIn(UUID.randomUUID(), supplierOrg, "supplier");
    assertThat(guard.canManageMaterial(material)).isTrue();

    signIn(UUID.randomUUID(), UUID.randomUUID(), "supplier");
    assertThatThrownBy(() -> guard.checkMaterialOwner(material))
        .isInstanceOf(ResourceNotFoundException.class);

    signIn(UUID.randomUUID(), null, "admin");
    assertThat(guard.canManageMaterial(material)).isTrue();
  }

  @Test
  void accessibleProjectIdsCombinesCompanyManagerAndTeamProjects() {
    UUID userId = UUID.randomUUID();
    UUID companyProject = UUID.randomUUID();
    UUID managed = UUID.randomUUID();
    when(projectRepository.findIdsByCompanyId(ownerOrg)).thenReturn(List.of(companyProject));
    when(projectRepository.findIdsByProjectManagerId(userId)).thenReturn(List.of(managed));
    when(teamMemberRepository.findActiveProjectsByUserId(userId))
        .thenReturn(List.of(ProjectTeamMember.builder().project(project).userId(userId).build()));
    signIn(userId, ownerOrg, "realtor");

    assertThat(guard.accessibleProjectIds())
        .containsExactlyInAnyOrder(companyProject, managed, project.getId());
  }
}
