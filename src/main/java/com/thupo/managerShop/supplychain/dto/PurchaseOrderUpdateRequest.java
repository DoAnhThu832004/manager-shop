package com.thupo.managerShop.supplychain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record PurchaseOrderUpdateRequest(
        @NotNull(message = "ID nhà cung cấp không được để trống")
        Long supplierId,

        @NotNull(message = "ID kho đích không được để trống")
        Long destinationLocationId,

        @Size(max = 255, message = "Số tham chiếu tối đa 255 ký tự")
        String referenceNumber,

        @Size(max = 5000, message = "Ghi chú tối đa 5000 ký tự")
        String noteToSupplier,

        @Size(max = 100, message = "Điều khoản thanh toán tối đa 100 ký tự")
        String paymentTerms,

        String currency,

        List<String> tags,

        @DecimalMin(value = "0.0", message = "Tiền thuế không được âm")
        BigDecimal taxTotal,

        @NotEmpty(message = "Danh sách mặt hàng đặt không được để trống")
        List<@Valid PurchaseOrderItemRequest> items
) {}
