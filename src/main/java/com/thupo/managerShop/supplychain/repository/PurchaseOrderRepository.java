package com.thupo.managerShop.supplychain.repository;

import com.thupo.managerShop.supplychain.entity.PurchaseOrder;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    boolean existsBySupplierIdAndStatusNotIn(Long supplierId, Collection<PurchaseOrderStatus> statuses);

    @Query("SELECT COALESCE(MAX(po.id), 0) FROM PurchaseOrder po")
    Long findMaxId();

    @Query("SELECT po FROM PurchaseOrder po WHERE " +
            "(:status IS NULL OR po.status = :status) AND " +
            "(:supplierId IS NULL OR po.supplier.id = :supplierId) AND " +
            "(:destinationLocationId IS NULL OR po.destinationLocation.id = :destinationLocationId) AND " +
            "(:from IS NULL OR po.createdAt >= :from) AND " +
            "(:to IS NULL OR po.createdAt <= :to) AND " +
            "(:q IS NULL OR LOWER(po.poNumber) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(po.referenceNumber) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<PurchaseOrder> searchPurchaseOrders(
            @Param("status") PurchaseOrderStatus status,
            @Param("supplierId") Long supplierId,
            @Param("destinationLocationId") Long destinationLocationId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("q") String q,
            Pageable pageable
    );
}
