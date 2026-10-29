package com.thupo.managerShop.supplychain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PurchaseOrderItemRequest(
        @NotNull(message = "ID biến thể không được để trống")
        Long variantId,

        @Min(value = 1, message = "Số lượng đặt phải lớn hơn 0")
        int quantityOrdered,

        @NotNull(message = "Đơn giá nhập không được để trống")
        @DecimalMin(value = "0.0", message = "Đơn giá nhập không được âm")
        BigDecimal unitCost
) {}
