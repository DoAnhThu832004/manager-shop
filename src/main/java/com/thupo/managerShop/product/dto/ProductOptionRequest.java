package com.thupo.managerShop.product.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ProductOptionRequest(
        @NotBlank(message = "Tên thuộc tính không được để trống")
        String name,
        Integer position,
        List<String> values
) {}
