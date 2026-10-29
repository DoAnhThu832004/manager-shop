package com.thupo.managerShop.supplychain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryTransferReceiveItemRequest(
        @NotNull(message = "ID dòng chuyển hàng không được để trống")
        Long itemId,

        @Min(value = 1, message = "Số lượng nhận phải lớn hơn 0")
        int quantity
) {}
