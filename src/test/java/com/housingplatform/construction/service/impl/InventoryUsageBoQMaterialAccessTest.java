package com.housingplatform.construction.service.impl;

import static com.housingplatform.construction.ConstructionTestAuth.signIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.construction.ConstructionTestAuth;
import com.housingplatform.construction.domain.BillOfQuantities;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.domain.Material;
import com.housingplatform.construction.domain.MaterialInventory;
import com.housingplatform.construction.domain.MaterialUsage;
import com.housingplatform.construction.dto.BillOfQuantitiesRequest;
import com.housingplatform.construction.dto.BillOfQuantitiesResponse;
import com.housingplatform.construction.dto.MaterialInventoryResponse;
import com.housingplatform.construction.dto.MaterialRequest;
import com.housingplatform.construction.dto.MaterialResponse;
import com.housingplatform.construction.dto.MaterialUsageRequest;
import com.housingplatform.construction.dto.MaterialUsageResponse;
import com.housingplatform.construction.repository.BillOfQuantitiesRepository;
import com.housingplatform.construction.repository.ConstructionPhaseRepository;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.construction.repository.MaterialInventoryRepository;
import com.housingplatform.construction.repository.MaterialOrderRepository;
import com.housingplatform.construction.repository.MaterialRepository;
import com.housingplatform.construction.repository.MaterialUsageRepository;
import com.housingplatform.construction.repository.ProjectTeamMemberRepository;
import com.housingplatform.construction.service.BillOfQuantitiesMapper;
import com.housingplatform.construction.service.ConstructionAccessGuard;
import com.housingplatform.construction.service.MaterialInventoryMapper;
import com.housingplatform.construction.service.MaterialMapper;
import com.housingplatform.construction.service.MaterialUsageMapper;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
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
class InventoryUsageBoQMaterialAccessTest {

  @Mock private ConstructionProjectRepository projectRepository;
  @Mock private ConstructionPhaseRepository phaseRepository;
  @Mock private ProjectTeamMemberRepository teamMemberRepository;
  @Mock private PropertyRepository propertyRepository;
  @Mock private MaterialInventoryRepository inventoryRepository;
  @Mock private MaterialRepository materialRepository;
  @Mock private MaterialUsageRepository usageRepository;
  @Mock private MaterialOrderRepository orderRepository;
  @Mock private BillOfQuantitiesRepository boqRepository;
  @Mock private MaterialInventoryMapper inventoryMapper;
  @Mock private MaterialUsageMapper usageMapper;
  @Mock private BillOfQuantitiesMapper boqMapper;
  @Mock private MaterialMapper materialMapper;

  private MaterialInventoryServiceImpl inventoryService;
  private MaterialUsageServiceImpl usageService;
  private BillOfQuantitiesServiceImpl boqService;
  private MaterialServiceImpl materialService;

  private final UUID ownerOrg = UUID.randomUUID();
  private final UUID otherOrg = UUID.randomUUID();
  private ConstructionProject project;
  private ConstructionProject foreignProject;

  @BeforeEach
  void setUp() {
    ConstructionAccessGuard guard =
        new ConstructionAccessGuard(projectRepository, teamMemberRepository, propertyRepository);
    inventoryService =
        new MaterialInventoryServiceImpl(
            inventoryRepository, materialRepository, inventoryMapper, guard);
    usageService =
        new MaterialUsageServiceImpl(
            usageRepository,
            usageMapper,
            projectRepository,
            phaseRepository,
            materialRepository,
            inventoryRepository,
            orderRepository,
            guard);
    boqService = new BillOfQuantitiesServiceImpl(boqRepository, boqMapper, guard);
    materialService = new MaterialServiceImpl(materialRepository, materialMapper, guard);

    project =
        ConstructionProject.builder().id(UUID.randomUUID()).realEstateCompanyId(ownerOrg).build();
    foreignProject =
        ConstructionProject.builder().id(UUID.randomUUID()).realEstateCompanyId(otherOrg).build();
    when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
    when(projectRepository.findById(foreignProject.getId()))
        .thenReturn(Optional.of(foreignProject));
    when(inventoryMapper.toResponse(any())).thenAnswer(inv -> new MaterialInventoryResponse());
    when(usageMapper.toResponse(any())).thenAnswer(inv -> new MaterialUsageResponse());
    when(boqMapper.toResponse(any())).thenAnswer(inv -> new BillOfQuantitiesResponse());
    when(materialMapper.toResponse(any())).thenAnswer(inv -> new MaterialResponse());
  }

  @AfterEach
  void tearDown() {
    ConstructionTestAuth.clear();
  }

  // ------------------------------------------------------------------ inventory

  private MaterialInventory inventory(UUID projectId) {
    return MaterialInventory.builder()
        .id(UUID.randomUUID())
        .materialId(UUID.randomUUID())
        .projectId(projectId)
        .quantity(BigDecimal.TEN)
        .reservedQuantity(BigDecimal.ZERO)
        .availableQuantity(BigDecimal.TEN)
        .status(MaterialInventory.InventoryStatus.IN_STOCK)
        .build();
  }

  @Test
  void inventoryOfOwnProjectIsAccessibleForeignIsNotFound() {
    MaterialInventory own = inventory(project.getId());
    MaterialInventory foreign = inventory(foreignProject.getId());
    when(inventoryRepository.findById(own.getId())).thenReturn(Optional.of(own));
    when(inventoryRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
    when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    signIn(UUID.randomUUID(), ownerOrg, "realtor");

    assertThat(inventoryService.getInventoryById(own.getId())).isNotNull();
    inventoryService.reserveInventory(own.getId(), BigDecimal.ONE);
    assertThat(own.getReservedQuantity()).isEqualByComparingTo("1");

    assertThatThrownBy(() -> inventoryService.getInventoryById(foreign.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(
            () -> inventoryService.adjustInventory(foreign.getId(), BigDecimal.ONE, null))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(foreign.getQuantity()).isEqualByComparingTo("10");
  }

  @Test
  void warehouseInventoryIsAdminOnly() {
    MaterialInventory warehouse = inventory(null);
    when(inventoryRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
    signIn(UUID.randomUUID(), ownerOrg, "realtor");
    assertThatThrownBy(() -> inventoryService.getInventoryById(warehouse.getId()))
        .isInstanceOf(ResourceNotFoundException.class);

    signIn(UUID.randomUUID(), null, "admin");
    assertThat(inventoryService.getInventoryById(warehouse.getId())).isNotNull();
  }

  @Test
  void lowStockListIsScopedToCallersProjects() {
    when(inventoryRepository.findLowStockItems())
        .thenReturn(
            List.of(
                inventory(project.getId()), inventory(foreignProject.getId()), inventory(null)));
    when(projectRepository.findIdsByCompanyId(ownerOrg)).thenReturn(List.of(project.getId()));
    signIn(UUID.randomUUID(), ownerOrg, "realtor");
    assertThat(inventoryService.getLowStockItems()).hasSize(1);

    signIn(UUID.randomUUID(), null, "admin");
    assertThat(inventoryService.getLowStockItems()).hasSize(3);
  }

  // ------------------------------------------------------------------ usage

  @Test
  void usageFollowsProjectAccess() {
    MaterialUsage own =
        MaterialUsage.builder().id(UUID.randomUUID()).projectId(project.getId()).build();
    MaterialUsage foreign =
        MaterialUsage.builder().id(UUID.randomUUID()).projectId(foreignProject.getId()).build();
    when(usageRepository.findById(own.getId())).thenReturn(Optional.of(own));
    when(usageRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
    signIn(UUID.randomUUID(), ownerOrg, "realtor");

    assertThat(usageService.getUsageById(own.getId())).isNotNull();
    assertThatThrownBy(() -> usageService.getUsageById(foreign.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> usageService.deleteUsage(foreign.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(usageRepository, never()).delete(any());
  }

  @Test
  void cannotRecordUsageAgainstAnotherCompanysInventory() {
    MaterialInventory foreignStock = inventory(foreignProject.getId());
    when(inventoryRepository.findById(foreignStock.getId())).thenReturn(Optional.of(foreignStock));
    UUID materialId = UUID.randomUUID();
    when(materialRepository.findById(materialId))
        .thenReturn(Optional.of(Material.builder().id(materialId).build()));
    MaterialUsageRequest request = new MaterialUsageRequest();
    request.setProjectId(project.getId());
    request.setMaterialId(materialId);
    request.setInventoryId(foreignStock.getId());
    request.setQuantity(BigDecimal.ONE);
    signIn(UUID.randomUUID(), ownerOrg, "realtor");

    assertThatThrownBy(() -> usageService.recordUsage(UUID.randomUUID(), request))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(foreignStock.getQuantity()).isEqualByComparingTo("10");
    verify(inventoryRepository, never()).save(any());
  }

  // ------------------------------------------------------------------ bills of quantities

  @Test
  void boqFollowsProjectAccess() {
    BillOfQuantities own =
        BillOfQuantities.builder()
            .id(UUID.randomUUID())
            .project(project)
            .projectId(project.getId())
            .build();
    BillOfQuantities foreign =
        BillOfQuantities.builder()
            .id(UUID.randomUUID())
            .project(foreignProject)
            .projectId(foreignProject.getId())
            .build();
    when(boqRepository.findById(own.getId())).thenReturn(Optional.of(own));
    when(boqRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
    signIn(UUID.randomUUID(), ownerOrg, "realtor");

    assertThat(boqService.getBoQById(own.getId())).isNotNull();
    assertThatThrownBy(() -> boqService.getBoQById(foreign.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> boqService.deleteBoQ(foreign.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(boqRepository, never()).delete(any());

    signIn(UUID.randomUUID(), null, "admin");
    assertThat(boqService.getBoQById(foreign.getId())).isNotNull();
  }

  @Test
  void cannotCreateBoQOnAnotherCompanysProject() {
    BillOfQuantitiesRequest request = new BillOfQuantitiesRequest();
    request.setName("BoQ");
    request.setProjectId(foreignProject.getId());
    request.setItems(List.of());
    when(boqMapper.toEntity(any())).thenAnswer(inv -> BillOfQuantities.builder().build());
    signIn(UUID.randomUUID(), ownerOrg, "realtor");

    assertThatThrownBy(() -> boqService.createBoQ(request))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(boqRepository, never()).save(any());
  }

  // ------------------------------------------------------------------ materials

  @Test
  void onlyOwningSupplierCanEditOrDeleteMaterial() {
    UUID supplierOrg = UUID.randomUUID();
    Material material = Material.builder().id(UUID.randomUUID()).supplierId(supplierOrg).build();
    when(materialRepository.findById(material.getId())).thenReturn(Optional.of(material));
    when(materialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    MaterialRequest request = new MaterialRequest();

    signIn(UUID.randomUUID(), UUID.randomUUID(), "supplier");
    assertThatThrownBy(() -> materialService.updateMaterial(null, material.getId(), request))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> materialService.deleteMaterial(null, material.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(materialRepository, never()).delete(any(Material.class));

    signIn(UUID.randomUUID(), supplierOrg, "supplier");
    assertThat(materialService.updateMaterial(null, material.getId(), request)).isNotNull();
    materialService.deleteMaterial(null, material.getId());
    verify(materialRepository).delete(material);

    // Catalog reads stay open to any authenticated user.
    signIn(UUID.randomUUID(), null, "buyer");
    assertThat(materialService.getMaterialById(material.getId())).isNotNull();
  }
}
