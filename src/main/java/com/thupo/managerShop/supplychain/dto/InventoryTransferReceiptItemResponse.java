package com.thupo.managerShop.supplychain.dto;

public record InventoryTransferReceiptItemResponse(
        Long id,
        Long itemId,
        Long variantId,
        String variantTitle,
        String sku,
        int quantity
) {}
