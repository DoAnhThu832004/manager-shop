package com.thupo.managerShop.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductVariantResponse(
        Long id,
        String gid,
        String title,
        String option1,
        String option2,
        String option3,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal costPerItem,
        boolean chargeTax,
        String unitPriceMeasure,
        String sku,
        String barcode,
        boolean inventoryTracked,
        boolean sellWhenOutOfStock,
        boolean isPhysical,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String countryOfOrigin,
        String hsCode,
        int position,
        Instant createdAt,
        Instant updatedAt
) {}
