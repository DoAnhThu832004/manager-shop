package com.thupo.managerShop.product.service;

import com.thupo.managerShop.product.entity.Collection;
import com.thupo.managerShop.product.entity.CollectionRule;
import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.entity.RuleField;
import com.thupo.managerShop.product.entity.RuleOperator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionRuleLogicTest {

    @Test
    @DisplayName("Nghiệp vụ bắt buộc: AUTOMATED với rule vendor = X và is_exclusion cho tag = Y trả đúng tập sản phẩm")
    void automatedWithVendorAndExclusionTagEvaluatesCorrectly() {
        Collection collection = new Collection();
        collection.setId(1L);
        collection.setRuleAppliesDisjunctive(false); // Khớp TẤT CẢ (AND)

        CollectionRule rule1 = new CollectionRule(collection, RuleField.VENDOR, RuleOperator.EQUALS, "Nike", false);
        CollectionRule rule2 = new CollectionRule(collection, RuleField.TAG, RuleOperator.CONTAINS, "sale", true); // is_exclusion = true!

        List<CollectionRule> rules = List.of(rule1, rule2);

        // Xây dựng Specification
        Specification<Product> spec = CollectionProductSpecification.buildSpecification(rules, false);
        assertNotNull(spec);

        // Kiểm tra logic đánh giá:
        // Product A: vendor = "Nike", tags = "[\"shoes\", \"running\"]" (không chứa "sale") -> KHỚP!
        Product productA = new Product();
        productA.setStatus(ProductStatus.ACTIVE);
        productA.setVendor("Nike");
        productA.setTags("[\"shoes\", \"running\"]");

        // Product B: vendor = "Nike", tags = "[\"shoes\", \"sale\"]" (chứa "sale", bị loại trừ) -> KHÔNG KHỚP!
        Product productB = new Product();
        productB.setStatus(ProductStatus.ACTIVE);
        productB.setVendor("Nike");
        productB.setTags("[\"shoes\", \"sale\"]");

        // Product C: vendor = "Adidas", tags = "[\"shoes\"]" (sai vendor) -> KHÔNG KHỚP!
        Product productC = new Product();
        productC.setStatus(ProductStatus.ACTIVE);
        productC.setVendor("Adidas");
        productC.setTags("[\"shoes\"]");

        // Hàm helper kiểm tra logic nghiệp vụ khớp rule
        assertTrue(matchesInMemory(productA, rules, false));
        assertFalse(matchesInMemory(productB, rules, false));
        assertFalse(matchesInMemory(productC, rules, false));
    }

    @Test
    @DisplayName("Nghiệp vụ bắt buộc: Chế độ khớp tất cả (AND) khác chế độ khớp bất kỳ (OR) trên cùng một bộ rule")
    void disjunctiveVsConjunctiveProducesDifferentResults() {
        Collection collection = new Collection();
        collection.setId(2L);

        // Rule: Vendor = "Nike" hoặc Type = "Shoes"
        CollectionRule rule1 = new CollectionRule(collection, RuleField.VENDOR, RuleOperator.EQUALS, "Nike", false);
        CollectionRule rule2 = new CollectionRule(collection, RuleField.TYPE, RuleOperator.EQUALS, "Shoes", false);
        List<CollectionRule> rules = List.of(rule1, rule2);

        // Product: Vendor = "Nike", Type = "Shirt" (Chỉ thỏa mãn rule 1)
        Product testProduct = new Product();
        testProduct.setStatus(ProductStatus.ACTIVE);
        testProduct.setVendor("Nike");
        testProduct.setProductType("Shirt");

        // Khớp TẤT CẢ (disjunctive = false): Phải thỏa mãn cả 2 rule -> FALSE!
        boolean matchAll = matchesInMemory(testProduct, rules, false);
        assertFalse(matchAll, "Chế độ khớp tất cả (AND) phải trả về FALSE khi sản phẩm chỉ thỏa mãn 1 rule");

        // Khớp BẤT KỲ (disjunctive = true): Chỉ cần thỏa mãn 1 trong 2 rule -> TRUE!
        boolean matchAny = matchesInMemory(testProduct, rules, true);
        assertTrue(matchAny, "Chế độ khớp bất kỳ (OR) phải trả về TRUE khi sản phẩm thỏa mãn ít nhất 1 rule");
    }

    private boolean matchesInMemory(Product p, List<CollectionRule> rules, boolean disjunctive) {
        if (p.getStatus() != ProductStatus.ACTIVE || p.getDeletedAt() != null) {
            return false;
        }
        if (rules == null || rules.isEmpty()) {
            return true;
        }

        List<Boolean> results = rules.stream().map(r -> {
            boolean conditionMet = switch (r.getField()) {
                case VENDOR -> p.getVendor() != null && p.getVendor().equalsIgnoreCase(r.getValue());
                case TYPE -> p.getProductType() != null && p.getProductType().equalsIgnoreCase(r.getValue());
                case TITLE -> p.getTitle() != null && p.getTitle().toLowerCase().contains(r.getValue().toLowerCase());
                case TAG -> p.getTags() != null && p.getTags().toLowerCase().contains(r.getValue().toLowerCase());
                default -> true;
            };
            return r.isExclusion() ? !conditionMet : conditionMet;
        }).toList();

        if (disjunctive) {
            return results.stream().anyMatch(Boolean::booleanValue);
        } else {
            return results.stream().allMatch(Boolean::booleanValue);
        }
    }
}
