package com.thupo.managerShop.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.product.dto.ProductListResponse;
import com.thupo.managerShop.product.dto.ProductMediaResponse;
import com.thupo.managerShop.product.dto.ProductOptionResponse;
import com.thupo.managerShop.product.dto.ProductResponse;
import com.thupo.managerShop.product.dto.ProductVariantResponse;
import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductMedia;
import com.thupo.managerShop.product.entity.ProductOption;
import com.thupo.managerShop.product.entity.ProductOptionValue;
import com.thupo.managerShop.product.entity.ProductVariant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mapper viết tay chuyển đổi giữa Entity và DTO theo quy ước không dùng thư viện tự động.
 */
@Component
public class ProductMapper {

    private final ObjectMapper objectMapper;

    public ProductMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProductResponse toResponse(Product product, int totalInventory) {
        if (product == null) {
            return null;
        }

        List<ProductOptionResponse> optionResponses = product.getOptions() != null
                ? product.getOptions().stream().map(this::toOptionResponse).toList()
                : Collections.emptyList();

        List<ProductVariantResponse> variantResponses = product.getVariants() != null
                ? product.getVariants().stream()
                        .filter(v -> v.getDeletedAt() == null)
                        .map(this::toVariantResponse)
                        .toList()
                : Collections.emptyList();

        List<ProductMediaResponse> mediaResponses = product.getMedia() != null
                ? product.getMedia().stream().map(this::toMediaResponse).toList()
                : Collections.emptyList();

        return new ProductResponse(
                product.getId(),
                "gid://shopify/Product/" + product.getId(),
                product.getTitle(),
                product.getDescription(),
                product.getStatus(),
                product.getCategoryId(),
                product.getCategoryName(),
                product.getProductType(),
                product.getVendor(),
                parseTags(product.getTags()),
                product.getThemeTemplate(),
                totalInventory,
                optionResponses,
                variantResponses,
                mediaResponses,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public ProductListResponse toListResponse(Product product, int totalInventory) {
        if (product == null) {
            return null;
        }

        int activeVariantsCount = 0;
        if (product.getVariants() != null) {
            activeVariantsCount = (int) product.getVariants().stream()
                    .filter(v -> v.getDeletedAt() == null)
                    .count();
        }

        String primaryImageUrl = null;
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            primaryImageUrl = product.getMedia().get(0).getUrl();
        }

        return new ProductListResponse(
                product.getId(),
                "gid://shopify/Product/" + product.getId(),
                product.getTitle(),
                product.getStatus(),
                product.getVendor(),
                product.getProductType(),
                totalInventory,
                activeVariantsCount,
                primaryImageUrl,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public ProductOptionResponse toOptionResponse(ProductOption option) {
        List<String> values = option.getValues() != null
                ? option.getValues().stream().map(ProductOptionValue::getValue).toList()
                : Collections.emptyList();

        return new ProductOptionResponse(
                option.getId(),
                option.getName(),
                option.getPosition(),
                values
        );
    }

    public ProductVariantResponse toVariantResponse(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                "gid://shopify/ProductVariant/" + variant.getId(),
                variant.getTitle(),
                variant.getOption1(),
                variant.getOption2(),
                variant.getOption3(),
                variant.getPrice(),
                variant.getCompareAtPrice(),
                variant.getCostPerItem(),
                variant.isChargeTax(),
                variant.getUnitPriceMeasure(),
                variant.getSku(),
                variant.getBarcode(),
                variant.isInventoryTracked(),
                variant.isSellWhenOutOfStock(),
                variant.isPhysical(),
                variant.getWeightKg(),
                variant.getLengthCm(),
                variant.getWidthCm(),
                variant.getHeightCm(),
                variant.getCountryOfOrigin(),
                variant.getHsCode(),
                variant.getPosition(),
                variant.getCreatedAt(),
                variant.getUpdatedAt()
        );
    }

    public ProductMediaResponse toMediaResponse(ProductMedia media) {
        return new ProductMediaResponse(
                media.getId(),
                media.getUrl(),
                media.getMediaType(),
                media.getAltText(),
                media.getPosition()
        );
    }

    public List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            return "[]";
        }
    }
}
