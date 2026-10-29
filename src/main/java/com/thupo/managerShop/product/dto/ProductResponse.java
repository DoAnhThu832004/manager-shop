package com.thupo.managerShop.product.dto;

import com.thupo.managerShop.product.entity.ProductStatus;
import java.time.Instant;
import java.util.List;

public record ProductResponse(
        Long id,
        String gid,
        String title,
        String description,
        ProductStatus status,
        String categoryId,
        String categoryName,
        String productType,
        String vendor,
        List<String> tags,
        String themeTemplate,
        int totalInventory,
        List<ProductOptionResponse> options,
        List<ProductVariantResponse> variants,
        List<ProductMediaResponse> media,
        Instant createdAt,
        Instant updatedAt
) {}
