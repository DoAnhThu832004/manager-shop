package com.thupo.managerShop.supplychain.dto;

import com.thupo.managerShop.supplychain.entity.PurchaseOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseOrderResponse(
        Long id,
        String poNumber,
        Long supplierId,
        String supplierName,
        Long destinationLocationId,
        String destinationLocationName,
        String referenceNumber,
        String noteToSupplier,
        String paymentTerms,
        String currency,
        List<String> tags,
        PurchaseOrderStatus status,
        BigDecimal subtotalCost,
        BigDecimal taxTotal,
        BigDecimal totalCost,
        Instant orderedAt,
        List<PurchaseOrderItemResponse> items,
        List<PurchaseOrderReceiptResponse> receipts,
        Instant createdAt,
        Instant updatedAt
) {}
