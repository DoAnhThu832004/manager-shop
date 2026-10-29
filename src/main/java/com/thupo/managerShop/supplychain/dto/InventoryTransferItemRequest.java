package com.thupo.managerShop.supplychain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryTransferItemRequest(
        @NotNull(message = "ID biến thể không được để trống")
        Long variantId,

        @Min(value = 1, message = "Số lượng chuyển phải lớn hơn 0")
        int quantityTransferred
) {}
