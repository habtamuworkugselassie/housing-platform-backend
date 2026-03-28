package com.housingplatform.identity.repository;

import com.housingplatform.identity.domain.SupplierSubcategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierSubcategoryRepository extends JpaRepository<SupplierSubcategory, UUID> {

  List<SupplierSubcategory> findByActiveTrueOrderBySortOrderAscNameAsc();

  List<SupplierSubcategory> findAllByOrderBySortOrderAscNameAsc();
}
