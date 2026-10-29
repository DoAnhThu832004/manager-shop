package com.thupo.managerShop.product.dto;

import com.thupo.managerShop.product.entity.MediaType;
import jakarta.validation.constraints.NotBlank;

public record ProductMediaRequest(
        Long id,
        @NotBlank(message = "Đường dẫn media không được để trống")
        String url,
        MediaType mediaType,
        String altText,
        Integer position
) {}
