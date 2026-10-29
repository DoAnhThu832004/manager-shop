package com.thupo.managerShop.supplychain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record InventoryAdjustmentRequest(
        @NotEmpty(message = "Danh sách điều chỉnh tồn kho không được để trống")
        List<@Valid InventoryAdjustmentItem> adjustments,
        String referenceType,
        String referenceId
) {}
