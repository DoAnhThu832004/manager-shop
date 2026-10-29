package com.thupo.managerShop.supplychain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record InventoryTransferReceiveRequest(
        @NotBlank(message = "Idempotency key không được để trống")
        @Size(max = 100, message = "Idempotency key tối đa 100 ký tự")
        String idempotencyKey,

        @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
        String note,

        @NotEmpty(message = "Danh sách mặt hàng nhận không được để trống")
        List<@Valid InventoryTransferReceiveItemRequest> items
) {}
