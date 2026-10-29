package com.thupo.managerShop.product.dto;

import com.thupo.managerShop.product.entity.RuleField;
import com.thupo.managerShop.product.entity.RuleOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CollectionRuleRequest(
        @NotNull(message = "Trường điều kiện (field) không được để trống")
        RuleField field,

        @NotNull(message = "Toán tử so sánh (operator) không được để trống")
        RuleOperator operator,

        @NotBlank(message = "Giá trị điều kiện (value) không được để trống")
        String value,

        Boolean isExclusion
) {}
