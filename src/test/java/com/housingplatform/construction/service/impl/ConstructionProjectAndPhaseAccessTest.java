package com.housingplatform.construction.service.impl;

import static com.housingplatform.construction.ConstructionTestAuth.signIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.construction.ConstructionTestAuth;
import com.housingplatform.construction.domain.ConstructionPhase;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.dto.ConstructionPhaseResponse;
import com.housingplatform.construction.dto.ConstructionProjectResponse;
import com.housingplatform.construction.repository.ConstructionPhaseRepository;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.construction.repository.ProjectTeamMemberRepository;
import com.housingplatform.construction.service.ConstructionAccessGuard;
import com.housingplatform.construction.service.ConstructionPhaseMapper;
import com.housingplatform.construction.service.ConstructionProjectMapper;
import com.housingplatform.identity.repository.OrganizationRepository;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConstructionProjectAndPhaseAccessTest {

  @Mock private ConstructionProjectRepository projectRepository;
  @Mock private ConstructionPhaseRepository phaseRepository;
  @Mock private ProjectTeamMemberRepository teamMemberRepository;
  @Mock private PropertyRepository propertyRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private ConstructionProjectMapper projectMapper;
  @Mock private ConstructionPhaseMapper phaseMapper;

  private ConstructionProjectServiceImpl projectService;
  private ConstructionPhaseServiceImpl phaseService;

  private final UUID ownerOrg = UUID.randomUUID();
  private ConstructionProject project;
  private ConstructionPhase phase;

  @BeforeEach
  void setUp() {
    ConstructionAccessGuard guard =
        new ConstructionAccessGuard(projectRepository, teamMemberRepository, propertyRepository);
    projectService =
        new ConstructionProjectServiceImpl(
            projectRepository,
            phaseRepository,
            projectMapper,
            phaseMapper,
            organizationRepository,
            guard);
    phaseService =
        new ConstructionPhaseServiceImpl(phaseRepository, projectRepository, phaseMapper, guard);

    project =
        ConstructionProject.builder()
            .id(UUID.randomUUID())
            .realEstateCompanyId(ownerOrg)
            .status(ConstructionProject.ProjectStatus.PLANNING)
            .build();
    phase = ConstructionPhase.builder().id(UUID.randomUUID()).project(project).build();
    when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
    when(phaseRepository.findById(phase.getId())).thenReturn(Optional.of(phase));
    when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(phaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(projectMapper.toResponse(any())).thenAnswer(inv -> new ConstructionProjectResponse());
    when(phaseMapper.toResponse(any())).thenAnswer(inv -> new ConstructionPhaseResponse());
  }

  @AfterEach
  void tearDown() {
    ConstructionTestAuth.clear();
  }

  @Test
  void ownerCanReadAndChangeStatus() {
    signIn(UUID.randomUUID(), ownerOrg, "realtor");
    assertThat(projectService.getProjectById(project.getId())).isNotNull();
    projectService.updateProjectStatus(
        project.getId(), ConstructionProject.ProjectStatus.IN_PROGRESS);
    assertThat(project.getStatus()).isEqualTo(ConstructionProject.ProjectStatus.IN_PROGRESS);
  }

  @Test
  void otherCompanyCannotReadOrMutateProject() {
    signIn(UUID.randomUUID(), UUID.randomUUID(), "buyer");
    assertThatThrownBy(() -> projectService.getProjectById(project.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(
            () ->
                projectService.updateProjectStatus(
                    project.getId(), ConstructionProject.ProjectStatus.CANCELLED))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> projectService.calculateProjectCosts(project.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(project.getStatus()).isEqualTo(ConstructionProject.ProjectStatus.PLANNING);
    verify(projectRepository, never()).save(any());
  }

  @Test
  void adminCanAccessAnyProject() {
    signIn(UUID.randomUUID(), null, "admin");
    assertThat(projectService.calculateProjectCosts(project.getId())).isNotNull();
  }

  @Test
  void propertyListIsFilteredToAccessibleProjects() {
    UUID propertyId = UUID.randomUUID();
    ConstructionProject foreign =
        ConstructionProject.builder()
            .id(UUID.randomUUID())
            .realEstateCompanyId(UUID.randomUUID())
            .build();
    when(projectRepository.findByPropertyId(propertyId)).thenReturn(List.of(project, foreign));
    signIn(UUID.randomUUID(), ownerOrg, "realtor");
    assertThat(projectService.getProjectsByProperty(propertyId)).hasSize(1);
  }

  @Test
  void managerListForSomeoneElseIsScopedToCallersCompany() {
    UUID otherManager = UUID.randomUUID();
    PageRequest pageable = PageRequest.of(0, 20);
    signIn(UUID.randomUUID(), null, "buyer");
    assertThat(projectService.getProjectsByManager(otherManager, pageable)).isEmpty();
    verify(projectRepository, never()).findByProjectManagerId(any(), any());
  }

  @Test
  void phaseAccessFollowsOwningProject() {
    signIn(UUID.randomUUID(), ownerOrg, "realtor");
    assertThat(phaseService.getPhaseById(phase.getId())).isNotNull();
    phaseService.updatePhaseCompletion(phase.getId(), 50);
    assertThat(phase.getCompletionPercentage()).isEqualTo(50);

    signIn(UUID.randomUUID(), UUID.randomUUID(), "realtor");
    assertThatThrownBy(() -> phaseService.getPhaseById(phase.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(
            () ->
                phaseService.updatePhaseStatus(
                    phase.getId(), ConstructionPhase.PhaseStatus.ON_HOLD))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> phaseService.getPhasesByProject(project.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
