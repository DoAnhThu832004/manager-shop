package com.thupo.managerShop.supplychain.repository;

import com.thupo.managerShop.supplychain.entity.InventoryLevel;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, Long> {

    Optional<InventoryLevel> findByVariantIdAndLocationId(Long variantId, Long locationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT il FROM InventoryLevel il WHERE il.variant.id = :variantId AND il.location.id = :locationId")
    Optional<InventoryLevel> findByVariantIdAndLocationIdForUpdate(@Param("variantId") Long variantId,
                                                                   @Param("locationId") Long locationId);

    @Query("SELECT COALESCE(SUM(il.available), 0) FROM InventoryLevel il " +
           "WHERE il.variant.product.id = :productId AND il.variant.deletedAt IS NULL")
    int sumAvailableByProductId(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(il.available), 0) FROM InventoryLevel il WHERE il.variant.id = :variantId")
    int sumAvailableByVariantId(@Param("variantId") Long variantId);

    @Query("SELECT il FROM InventoryLevel il " +
           "WHERE (:locationId IS NULL OR il.location.id = :locationId) " +
           "AND (:variantId IS NULL OR il.variant.id = :variantId) " +
           "AND (:productId IS NULL OR il.variant.product.id = :productId) " +
           "AND (:q IS NULL OR LOWER(il.variant.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(il.variant.sku) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(il.variant.product.title) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:lowStockThreshold IS NULL OR il.available <= :lowStockThreshold)")
    Page<InventoryLevel> searchInventory(@Param("locationId") Long locationId,
                                         @Param("variantId") Long variantId,
                                         @Param("productId") Long productId,
                                         @Param("q") String q,
                                         @Param("lowStockThreshold") Integer lowStockThreshold,
                                         Pageable pageable);
}
