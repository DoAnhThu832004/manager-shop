package com.thupo.managerShop.supplychain.dto;

import jakarta.validation.constraints.NotNull;

public record InventoryAdjustmentItem(
        @NotNull(message = "ID biến thể (variantId) không được để trống")
        Long variantId,

        @NotNull(message = "ID địa điểm kho (locationId) không được để trống")
        Long locationId,

        Integer delta,
        Integer newAvailable,
        String reason
) {}
