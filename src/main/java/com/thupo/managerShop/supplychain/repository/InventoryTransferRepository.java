package com.thupo.managerShop.supplychain.repository;

import com.thupo.managerShop.supplychain.entity.InventoryTransfer;
import com.thupo.managerShop.supplychain.entity.TransferStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, Long> {

    Optional<InventoryTransfer> findByTransferNumber(String transferNumber);

    @Query("SELECT COALESCE(MAX(t.id), 0) FROM InventoryTransfer t")
    Long findMaxId();

    @Query("SELECT t FROM InventoryTransfer t WHERE " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:originLocationId IS NULL OR t.originLocation.id = :originLocationId) AND " +
            "(:destinationLocationId IS NULL OR t.destinationLocation.id = :destinationLocationId) AND " +
            "(:from IS NULL OR t.createdAt >= :from) AND " +
            "(:to IS NULL OR t.createdAt <= :to) AND " +
            "(:q IS NULL OR LOWER(t.transferNumber) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(t.referenceName) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<InventoryTransfer> searchTransfers(
            @Param("status") TransferStatus status,
            @Param("originLocationId") Long originLocationId,
            @Param("destinationLocationId") Long destinationLocationId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("q") String q,
            Pageable pageable
    );
}
