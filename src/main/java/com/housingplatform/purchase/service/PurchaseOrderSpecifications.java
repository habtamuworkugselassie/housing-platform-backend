package com.housingplatform.purchase.service;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.dto.AdminPurchaseOrderFilter;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Criteria for the admin listing; each non-null filter field adds one predicate. */
public final class PurchaseOrderSpecifications {

  private PurchaseOrderSpecifications() {}

  public static Specification<PropertyPurchaseOrder> forAdmin(AdminPurchaseOrderFilter filter) {
    AdminPurchaseOrderFilter f = filter == null ? AdminPurchaseOrderFilter.none() : filter;
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (f.status() != null) {
        predicates.add(cb.equal(root.get("status"), f.status()));
      }
      if (f.purchaseType() != null) {
        predicates.add(cb.equal(root.get("purchaseType"), f.purchaseType()));
      }
      if (f.realEstateCompanyId() != null) {
        predicates.add(cb.equal(root.get("realEstateCompanyId"), f.realEstateCompanyId()));
      }
      if (f.buyerId() != null) {
        predicates.add(cb.equal(root.get("buyerId"), f.buyerId()));
      }
      if (f.bankId() != null) {
        predicates.add(cb.equal(root.join("financing", JoinType.LEFT).get("bankId"), f.bankId()));
      }
      if (f.createdFrom() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.createdFrom()));
      }
      if (f.createdTo() != null) {
        predicates.add(cb.lessThan(root.get("createdAt"), f.createdTo()));
      }
      if (f.query() != null && !f.query().isBlank()) {
        String like = "%" + f.query().trim().toLowerCase(Locale.ROOT) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("orderNumber")), like),
                cb.like(root.get("contactPhone"), like.replace(" ", "")),
                cb.like(cb.lower(root.get("contactEmail")), like)));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
