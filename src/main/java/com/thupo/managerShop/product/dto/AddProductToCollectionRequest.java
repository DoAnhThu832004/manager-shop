package com.thupo.managerShop.product.dto;

import jakarta.validation.constraints.NotNull;

public record AddProductToCollectionRequest(
        @NotNull(message = "ID sản phẩm không được để trống")
        Long productId,
        Integer position
) {}
