package com.thupo.managerShop.supplychain.repository;

import com.thupo.managerShop.supplychain.entity.PurchaseOrderItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

    List<PurchaseOrderItem> findByPurchaseOrderId(Long purchaseOrderId);

    Optional<PurchaseOrderItem> findByPurchaseOrderIdAndVariantId(Long purchaseOrderId, Long variantId);
}
