package com.thupo.managerShop.product.dto;

import com.thupo.managerShop.product.entity.RuleField;
import com.thupo.managerShop.product.entity.RuleOperator;

public record CollectionRuleResponse(
        Long id,
        RuleField field,
        RuleOperator operator,
        String value,
        boolean isExclusion
) {}
