package com.thupo.managerShop.product.dto;

import com.thupo.managerShop.product.entity.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductUpdateRequest(
        @NotBlank(message = "Tiêu đề sản phẩm không được để trống")
        @Size(max = 255, message = "Tiêu đề sản phẩm tối đa 255 ký tự")
        String title,

        String description,
        ProductStatus status,
        String categoryId,
        String categoryName,
        String productType,
        String vendor,
        List<String> tags,
        String themeTemplate,
        List<ProductOptionRequest> options,
        List<ProductVariantRequest> variants,
        List<ProductMediaRequest> media
) {}
