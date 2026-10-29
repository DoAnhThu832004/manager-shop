package com.thupo.managerShop.supplychain.repository;

import com.thupo.managerShop.supplychain.entity.InventoryMovement;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query("SELECT im FROM InventoryMovement im " +
           "WHERE (:variantId IS NULL OR im.variant.id = :variantId) " +
           "AND (:locationId IS NULL OR im.location.id = :locationId) " +
           "AND (:from IS NULL OR im.createdAt >= :from) " +
           "AND (:to IS NULL OR im.createdAt <= :to) " +
           "ORDER BY im.createdAt DESC, im.id DESC")
    Page<InventoryMovement> searchMovements(@Param("variantId") Long variantId,
                                            @Param("locationId") Long locationId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to,
                                            Pageable pageable);
}
