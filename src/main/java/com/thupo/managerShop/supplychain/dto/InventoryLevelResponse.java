package com.thupo.managerShop.supplychain.dto;

import java.time.Instant;

public record InventoryLevelResponse(
        Long id,
        Long variantId,
        String variantTitle,
        String sku,
        Long productId,
        String productTitle,
        Long locationId,
        String locationName,
        int available,
        int onHand,
        int committed,
        int unavailable,
        int incoming,
        Instant updatedAt
) {}
