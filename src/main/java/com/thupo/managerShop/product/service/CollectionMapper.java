package com.thupo.managerShop.product.service;

import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.product.dto.CollectionListResponse;
import com.thupo.managerShop.product.dto.CollectionResponse;
import com.thupo.managerShop.product.dto.CollectionRuleResponse;
import com.thupo.managerShop.product.dto.ProductListResponse;
import com.thupo.managerShop.product.entity.Collection;
import com.thupo.managerShop.product.entity.CollectionRule;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mapper chuyển đổi giữa Entity và DTO cho Module Bộ sưu tập.
 */
@Component
public class CollectionMapper {

    public CollectionListResponse toListResponse(Collection collection, long productsCount) {
        if (collection == null) {
            return null;
        }
        return new CollectionListResponse(
                collection.getId(),
                "gid://shopify/Collection/" + collection.getId(),
                collection.getTitle(),
                collection.getImageUrl(),
                collection.getCollectionType(),
                productsCount,
                collection.getCreatedAt(),
                collection.getUpdatedAt()
        );
    }

    public CollectionResponse toResponse(Collection collection,
                                         long productsCount,
                                         List<ProductListResponse> products,
                                         PageMeta productsMeta) {
        if (collection == null) {
            return null;
        }

        List<CollectionRuleResponse> ruleResponses = collection.getRules() != null
                ? collection.getRules().stream().map(this::toRuleResponse).toList()
                : Collections.emptyList();

        return new CollectionResponse(
                collection.getId(),
                "gid://shopify/Collection/" + collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getImageUrl(),
                collection.getCollectionType(),
                collection.isRuleAppliesDisjunctive(),
                productsCount,
                ruleResponses,
                products,
                productsMeta,
                collection.getCreatedAt(),
                collection.getUpdatedAt()
        );
    }

    public CollectionRuleResponse toRuleResponse(CollectionRule rule) {
        if (rule == null) {
            return null;
        }
        return new CollectionRuleResponse(
                rule.getId(),
                rule.getField(),
                rule.getOperator(),
                rule.getValue(),
                rule.isExclusion()
        );
    }
}
