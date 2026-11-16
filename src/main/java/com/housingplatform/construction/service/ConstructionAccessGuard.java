package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.BillOfQuantities;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.domain.Material;
import com.housingplatform.construction.domain.MaterialInventory;
import com.housingplatform.construction.domain.MaterialOrder;
import com.housingplatform.construction.domain.ProjectTeamMember;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.construction.repository.ProjectTeamMemberRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Central object-level authorization for the construction module.
 *
 * <p>Every by-id read and every mutation in the construction services goes through this guard. A
 * caller who may not see a resource gets {@link ResourceNotFoundException} (404), never a 403, so
 * ids cannot be probed for existence.
 *
 * <p>Project access: admin / super-admin, OR the caller's organization owns the project ({@code
 * realEstateCompanyId}), OR the caller is the project manager, OR the caller is an ACTIVE {@link
 * ProjectTeamMember} of the project. Phases, usage, BoQs and project-bound inventory inherit the
 * owning project's rule. Material orders are additionally visible to the supplier organization the
 * order was placed with.
 */
@Component
@RequiredArgsConstructor
public class ConstructionAccessGuard {

  private final ConstructionProjectRepository projectRepository;
  private final ProjectTeamMemberRepository teamMemberRepository;
  private final PropertyRepository propertyRepository;

  // ---------------------------------------------------------------- caller identity

  private static boolean authenticated() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() != null;
  }

  public boolean isAdmin() {
    return authenticated() && (UserContext.isAdmin() || UserContext.isSuperAdmin());
  }

  public UUID currentUserId() {
    return UserContext.getCurrentUserIdOrNull();
  }

  public Optional<UUID> currentOrganizationId() {
    return authenticated() ? UserContext.getCurrentUserOrganizationId() : Optional.empty();
  }

  /** True when the caller's organization is {@code organizationId}. */
  public boolean isCurrentOrganization(UUID organizationId) {
    return organizationId != null
        && currentOrganizationId().map(organizationId::equals).orElse(false);
  }

  // ---------------------------------------------------------------- projects

  public boolean canAccessProject(ConstructionProject project) {
    if (project == null) {
      return false;
    }
    if (isAdmin() || isCurrentOrganization(project.getRealEstateCompanyId())) {
      return true;
    }
    UUID userId = currentUserId();
    if (userId == null) {
      return false;
    }
    if (userId.equals(project.getProjectManagerId())) {
      return true;
    }
    return project.getId() != null
        && teamMemberRepository
            .findByProjectIdAndUserId(project.getId(), userId)
            .filter(tm -> tm.getStatus() == ProjectTeamMember.MemberStatus.ACTIVE)
            .isPresent();
  }

  /** Loads a project the caller may access, or throws not-found. */
  public ConstructionProject requireProject(UUID projectId) {
    return projectRepository
        .findById(projectId)
        .filter(this::canAccessProject)
        .orElseThrow(() -> new ResourceNotFoundException("Construction Project", projectId));
  }

  /** Throws not-found for {@code resource}/{@code id} unless the caller may access the project. */
  public void checkProject(ConstructionProject project, String resource, Object id) {
    if (!canAccessProject(project)) {
      throw new ResourceNotFoundException(resource, id);
    }
  }

  /** Same as {@link #checkProject} for entities that only carry the project id. */
  public void checkProjectId(UUID projectId, String resource, Object id) {
    if (projectId == null || !canAccessProjectId(projectId)) {
      throw new ResourceNotFoundException(resource, id);
    }
  }

  public boolean canAccessProjectId(UUID projectId) {
    return projectId != null
        && (isAdmin()
            || projectRepository.findById(projectId).filter(this::canAccessProject).isPresent());
  }

  /**
   * Ids of every project the caller can access (organization-owned, managed, or team member). Used
   * to scope list endpoints that are not keyed by a single project. Not meaningful for admins, who
   * are unscoped; callers check {@link #isAdmin()} first.
   */
  public Set<UUID> accessibleProjectIds() {
    Set<UUID> ids = new HashSet<>();
    currentOrganizationId().ifPresent(org -> ids.addAll(projectRepository.findIdsByCompanyId(org)));
    UUID userId = currentUserId();
    if (userId != null) {
      ids.addAll(projectRepository.findIdsByProjectManagerId(userId));
      teamMemberRepository.findActiveProjectsByUserId(userId).stream()
          .map(tm -> tm.getProject() != null ? tm.getProject().getId() : null)
          .filter(Objects::nonNull)
          .forEach(ids::add);
    }
    return ids;
  }

  // ---------------------------------------------------------------- inventory

  /**
   * Project-bound inventory follows project access. Warehouse inventory (no projectId) has no
   * owning organization or user recorded on the entity, so there is no sane way to scope it: it is
   * admin-only.
   */
  public boolean canAccessInventory(MaterialInventory inventory) {
    if (isAdmin()) {
      return true;
    }
    return inventory.getProjectId() != null && canAccessProjectId(inventory.getProjectId());
  }

  public void checkInventory(MaterialInventory inventory) {
    if (!canAccessInventory(inventory)) {
      throw new ResourceNotFoundException("Material Inventory", inventory.getId());
    }
  }

  // ---------------------------------------------------------------- orders

  /**
   * Buyer side of an order: project access when the order is tied to a project, otherwise only the
   * user who placed it (orders carry no buyer organization).
   */
  public boolean isOrderBuyer(MaterialOrder order) {
    if (order.getProject() != null) {
      return canAccessProject(order.getProject());
    }
    UUID userId = currentUserId();
    return userId != null && userId.equals(order.getOrderedBy());
  }

  /** Supplier side of an order: the caller's organization is the order's supplier. */
  public boolean isOrderSupplier(MaterialOrder order) {
    return isCurrentOrganization(order.getSupplierId());
  }

  public boolean canAccessOrder(MaterialOrder order) {
    return isAdmin() || isOrderBuyer(order) || isOrderSupplier(order);
  }

  public void checkOrder(MaterialOrder order, Object id) {
    if (!canAccessOrder(order)) {
      throw new ResourceNotFoundException("Material Order", id);
    }
  }

  // ---------------------------------------------------------------- bills of quantities

  /**
   * A BoQ bound to a project follows project access. A property-only BoQ is accessible to the
   * organization that owns the property or the property's agent. A BoQ with neither is admin-only.
   */
  public boolean canAccessBoQ(BillOfQuantities boq) {
    if (isAdmin()) {
      return true;
    }
    if (boq.getProject() != null) {
      return canAccessProject(boq.getProject());
    }
    if (boq.getProjectId() != null) {
      return canAccessProjectId(boq.getProjectId());
    }
    return canAccessProperty(boq.getPropertyId());
  }

  public boolean canAccessProperty(UUID propertyId) {
    if (propertyId == null) {
      return false;
    }
    if (isAdmin()) {
      return true;
    }
    UUID userId = currentUserId();
    return propertyRepository
        .findById(propertyId)
        .filter(
            p ->
                isCurrentOrganization(p.getRealEstateCompanyId())
                    || (userId != null && userId.equals(p.getAgentId())))
        .isPresent();
  }

  /** Ids of properties owned by the caller's organization or where the caller is the agent. */
  public Set<UUID> accessiblePropertyIds() {
    Set<UUID> ids = new HashSet<>();
    currentOrganizationId()
        .ifPresent(
            org ->
                propertyRepository.findByRealEstateCompanyId(org).forEach(p -> ids.add(p.getId())));
    UUID userId = currentUserId();
    if (userId != null) {
      propertyRepository.findByAgentId(userId).forEach(p -> ids.add(p.getId()));
    }
    return ids;
  }

  // ---------------------------------------------------------------- materials (catalog)

  /**
   * Catalog writes are limited to the owning supplier organization (or admin). Materials created
   * before ownership was keyed by organization stored the creating user's id in {@code supplierId};
   * that user keeps write access to them.
   */
  public boolean canManageMaterial(Material material) {
    if (isAdmin() || isCurrentOrganization(material.getSupplierId())) {
      return true;
    }
    UUID userId = currentUserId();
    return userId != null && userId.equals(material.getSupplierId());
  }

  public void checkMaterialOwner(Material material) {
    if (!canManageMaterial(material)) {
      throw new ResourceNotFoundException("Material", material.getId());
    }
  }
}
