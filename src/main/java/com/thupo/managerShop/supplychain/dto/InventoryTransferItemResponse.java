package com.thupo.managerShop.supplychain.dto;

public record InventoryTransferItemResponse(
        Long id,
        Long variantId,
        String variantTitle,
        String sku,
        Long productId,
        String productTitle,
        int quantityTransferred,
        int quantityReceived
) {}
