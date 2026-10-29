package com.thupo.managerShop.supplychain.repository;

import com.thupo.managerShop.supplychain.entity.InventoryTransferReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransferReceiptRepository extends JpaRepository<InventoryTransferReceipt, Long> {

    Optional<InventoryTransferReceipt> findByIdempotencyKey(String idempotencyKey);
}
