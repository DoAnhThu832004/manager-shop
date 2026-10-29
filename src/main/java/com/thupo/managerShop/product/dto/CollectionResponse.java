package com.thupo.managerShop.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.product.entity.CollectionType;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectionResponse(
        Long id,
        String gid,
        String title,
        String description,
        String imageUrl,
        CollectionType collectionType,
        boolean ruleAppliesDisjunctive,
        long productsCount,
        List<CollectionRuleResponse> rules,
        List<ProductListResponse> products,
        PageMeta productsMeta,
        Instant createdAt,
        Instant updatedAt
) {}
