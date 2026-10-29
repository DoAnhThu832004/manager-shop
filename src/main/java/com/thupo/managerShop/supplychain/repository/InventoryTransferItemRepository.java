package com.thupo.managerShop.supplychain.repository;

import com.thupo.managerShop.supplychain.entity.InventoryTransferItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransferItemRepository extends JpaRepository<InventoryTransferItem, Long> {

    List<InventoryTransferItem> findByTransferId(Long transferId);
}
