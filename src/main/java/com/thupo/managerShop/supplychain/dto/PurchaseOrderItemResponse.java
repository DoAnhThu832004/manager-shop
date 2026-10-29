package com.thupo.managerShop.supplychain.dto;

import java.math.BigDecimal;

public record PurchaseOrderItemResponse(
        Long id,
        Long variantId,
        String variantTitle,
        String sku,
        Long productId,
        String productTitle,
        int quantityOrdered,
        int quantityReceived,
        BigDecimal unitCost,
        BigDecimal lineTotal
) {}
