package com.thupo.managerShop.product.dto;

import java.math.BigDecimal;

public record ProductVariantRequest(
        Long id,
        String title,
        String option1,
        String option2,
        String option3,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal costPerItem,
        Boolean chargeTax,
        String unitPriceMeasure,
        String sku,
        String barcode,
        Boolean inventoryTracked,
        Boolean sellWhenOutOfStock,
        Boolean isPhysical,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String countryOfOrigin,
        String hsCode,
        Integer position
) {}
