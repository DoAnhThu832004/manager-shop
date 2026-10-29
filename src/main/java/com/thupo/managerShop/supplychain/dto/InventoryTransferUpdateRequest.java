package com.thupo.managerShop.supplychain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record InventoryTransferUpdateRequest(
        @NotNull(message = "ID kho nguồn không được để trống")
        Long originLocationId,

        @NotNull(message = "ID kho đích không được để trống")
        Long destinationLocationId,

        Instant transferDate,

        @Size(max = 255, message = "Tên tham chiếu tối đa 255 ký tự")
        String referenceName,

        @Size(max = 5000, message = "Ghi chú tối đa 5000 ký tự")
        String note,

        List<String> tags,

        Long linkedPurchaseOrderId,

        @NotEmpty(message = "Danh sách mặt hàng chuyển không được để trống")
        List<@Valid InventoryTransferItemRequest> items
) {}
