package com.housingplatform.construction.service.impl;

import static com.housingplatform.construction.ConstructionTestAuth.signIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.construction.ConstructionTestAuth;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.domain.MaterialOrder;
import com.housingplatform.construction.dto.MaterialOrderResponse;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.construction.repository.MaterialOrderItemRepository;
import com.housingplatform.construction.repository.MaterialOrderRepository;
import com.housingplatform.construction.repository.ProjectTeamMemberRepository;
import com.housingplatform.construction.service.ConstructionAccessGuard;
import com.housingplatform.construction.service.MaterialOrderMapper;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
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
class MaterialOrderAccessTest {

  @Mock private MaterialOrderRepository orderRepository;
  @Mock private MaterialOrderItemRepository orderItemRepository;
  @Mock private MaterialOrderMapper orderMapper;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private ConstructionProjectRepository projectRepository;
  @Mock private ProjectTeamMemberRepository teamMemberRepository;
  @Mock private PropertyRepository propertyRepository;

  private MaterialOrderServiceImpl service;

  private final UUID buyerOrg = UUID.randomUUID();
  private final UUID supplierOrg = UUID.randomUUID();
  private MaterialOrder order;

  @BeforeEach
  void setUp() {
    ConstructionAccessGuard guard =
        new ConstructionAccessGuard(projectRepository, teamMemberRepository, propertyRepository);
    service =
        new MaterialOrderServiceImpl(
            orderRepository,
            orderItemRepository,
            orderMapper,
            organizationRepository,
            projectRepository,
            guard);
    ConstructionProject project =
        ConstructionProject.builder().id(UUID.randomUUID()).realEstateCompanyId(buyerOrg).build();
    order =
        MaterialOrder.builder()
            .id(UUID.randomUUID())
            .orderNumber("MO-1")
            .project(project)
            .supplierId(supplierOrg)
            .orderedBy(UUID.randomUUID())
            .status(MaterialOrder.OrderStatus.PENDING)
            .build();
    when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.findByOrderNumber("MO-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orderMapper.toResponse(any())).thenAnswer(inv -> new MaterialOrderResponse());
  }

  @AfterEach
  void tearDown() {
    ConstructionTestAuth.clear();
  }

  @Test
  void buyingCompanyCanReadItsOrder() {
    signIn(UUID.randomUUID(), buyerOrg, "realtor");
    assertThat(service.getOrderById(order.getId())).isNotNull();
    assertThat(service.getOrderByOrderNumber("MO-1")).isNotNull();
  }

  @Test
  void otherCompanyGetsNotFound() {
    signIn(UUID.randomUUID(), UUID.randomUUID(), "realtor");
    assertThatThrownBy(() -> service.getOrderById(order.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.getOrderByOrderNumber("MO-1"))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(
            () -> service.updateOrderStatus(order.getId(), MaterialOrder.OrderStatus.CANCELLED))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.updateOrderDelivery(order.getId(), LocalDate.now()))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void supplierCanProgressItsOwnOrder() {
    signIn(UUID.randomUUID(), supplierOrg, "supplier");
    assertThat(service.getOrderById(order.getId())).isNotNull();
    service.updateOrderStatus(order.getId(), MaterialOrder.OrderStatus.SHIPPED);
    assertThat(order.getStatus()).isEqualTo(MaterialOrder.OrderStatus.SHIPPED);
    service.updateOrderDelivery(order.getId(), LocalDate.now());
    assertThat(order.getStatus()).isEqualTo(MaterialOrder.OrderStatus.DELIVERED);
  }

  @Test
  void supplierCannotSetBuyerOnlyStatuses() {
    signIn(UUID.randomUUID(), supplierOrg, "supplier");
    assertThatThrownBy(
            () -> service.updateOrderStatus(order.getId(), MaterialOrder.OrderStatus.RETURNED))
        .isInstanceOf(BusinessException.class);
    assertThat(order.getStatus()).isEqualTo(MaterialOrder.OrderStatus.PENDING);
  }

  @Test
  void anotherSupplierGetsNotFound() {
    signIn(UUID.randomUUID(), UUID.randomUUID(), "supplier");
    assertThatThrownBy(
            () -> service.updateOrderStatus(order.getId(), MaterialOrder.OrderStatus.CONFIRMED))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void supplierOrderBookIsOnlyVisibleToThatSupplier() {
    PageRequest pageable = PageRequest.of(0, 20);
    signIn(UUID.randomUUID(), UUID.randomUUID(), "supplier");
    assertThat(service.getOrdersBySupplier(supplierOrg, null, pageable)).isEmpty();
    verify(orderRepository, never()).findBySupplierId(any(), any());
  }

  @Test
  void adminCanSetAnyStatus() {
    signIn(UUID.randomUUID(), null, "admin");
    service.updateOrderStatus(order.getId(), MaterialOrder.OrderStatus.CONFIRMED);
    assertThat(order.getStatus()).isEqualTo(MaterialOrder.OrderStatus.CONFIRMED);
  }
}
