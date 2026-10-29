package com.thupo.managerShop.product.dto;

import com.thupo.managerShop.product.entity.ProductStatus;
import java.time.Instant;

public record ProductListResponse(
        Long id,
        String gid,
        String title,
        ProductStatus status,
        String vendor,
        String productType,
        int totalInventory,
        int variantsCount,
        String primaryImageUrl,
        Instant createdAt,
        Instant updatedAt
) {}
