package com.thupo.managerShop.product.dto;

import com.thupo.managerShop.product.entity.CollectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CollectionUpdateRequest(
        @NotBlank(message = "Tiêu đề bộ sưu tập không được để trống")
        @Size(max = 255, message = "Tiêu đề bộ sưu tập tối đa 255 ký tự")
        String title,

        String description,
        String imageUrl,
        CollectionType collectionType,
        Boolean ruleAppliesDisjunctive,
        List<CollectionRuleRequest> rules,
        Boolean confirmSwitch
) {}
