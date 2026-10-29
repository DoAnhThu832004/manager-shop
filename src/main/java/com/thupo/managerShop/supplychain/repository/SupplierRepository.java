package com.thupo.managerShop.supplychain.repository;

import com.thupo.managerShop.supplychain.entity.Supplier;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

    @Query("SELECT s FROM Supplier s WHERE s.deletedAt IS NULL AND " +
            "(:q IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(s.email) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR s.phone LIKE CONCAT('%', :q, '%'))")
    Page<Supplier> searchSuppliers(@Param("q") String q, Pageable pageable);
}
