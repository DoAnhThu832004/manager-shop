package com.thupo.managerShop.supplychain.repository;

import com.thupo.managerShop.supplychain.entity.PurchaseOrderReceipt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderReceiptRepository extends JpaRepository<PurchaseOrderReceipt, Long> {

    Optional<PurchaseOrderReceipt> findByIdempotencyKey(String idempotencyKey);

    List<PurchaseOrderReceipt> findByPurchaseOrderIdOrderByReceivedAtDesc(Long purchaseOrderId);
}
