package com.thupo.managerShop.product.dto;

import com.thupo.managerShop.product.entity.CollectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CollectionCreateRequest(
        @NotBlank(message = "Tiêu đề bộ sưu tập không được để trống")
        @Size(max = 255, message = "Tiêu đề bộ sưu tập tối đa 255 ký tự")
        String title,

        String description,
        String imageUrl,

        @NotNull(message = "Loại bộ sưu tập (collectionType) là bắt buộc (MANUAL hoặc AUTOMATED)")
        CollectionType collectionType,

        Boolean ruleAppliesDisjunctive,
        List<CollectionRuleRequest> rules,
        List<Long> productIds
) {}
