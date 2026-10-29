package com.thupo.managerShop.product.dto;

import com.thupo.managerShop.product.entity.CollectionType;
import java.time.Instant;

public record CollectionListResponse(
        Long id,
        String gid,
        String title,
        String imageUrl,
        CollectionType collectionType,
        long productsCount,
        Instant createdAt,
        Instant updatedAt
) {}
