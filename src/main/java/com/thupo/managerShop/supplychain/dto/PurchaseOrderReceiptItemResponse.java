package com.thupo.managerShop.supplychain.dto;

public record PurchaseOrderReceiptItemResponse(
        Long id,
        Long itemId,
        Long variantId,
        String variantTitle,
        String sku,
        int quantity
) {}
