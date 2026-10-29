package com.thupo.managerShop.product.service;

import com.thupo.managerShop.product.entity.CollectionRule;
import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.entity.ProductVariant;
import com.thupo.managerShop.product.entity.RuleField;
import com.thupo.managerShop.product.entity.RuleOperator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Xây dựng truy vấn động Specification cho Bộ sưu tập tự động (AUTOMATED) dựa trên bộ quy tắc.
 * Đảm bảo chỉ sản phẩm ACTIVE và chưa bị xóa mềm được tính.
 */
public class CollectionProductSpecification {

    public static Specification<Product> buildSpecification(List<CollectionRule> rules, boolean disjunctive) {
        return (Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            query.distinct(true);

            // Ràng buộc cơ sở: Sản phẩm ACTIVE và deleted_at IS NULL
            Predicate basePredicate = cb.and(
                    cb.isNull(root.get("deletedAt")),
                    cb.equal(root.get("status"), ProductStatus.ACTIVE)
            );

            if (rules == null || rules.isEmpty()) {
                return basePredicate;
            }

            List<Predicate> rulePredicates = new ArrayList<>();
            for (CollectionRule rule : rules) {
                Predicate p = buildRulePredicate(rule, root, cb);
                if (p != null) {
                    if (rule.isExclusion()) {
                        p = cb.not(p);
                    }
                    rulePredicates.add(p);
                }
            }

            if (rulePredicates.isEmpty()) {
                return basePredicate;
            }

            Predicate combinedRules;
            if (disjunctive) {
                // Khớp BẤT KỲ điều kiện nào (OR)
                combinedRules = cb.or(rulePredicates.toArray(new Predicate[0]));
            } else {
                // Khớp TẤT CẢ điều kiện (AND)
                combinedRules = cb.and(rulePredicates.toArray(new Predicate[0]));
            }

            return cb.and(basePredicate, combinedRules);
        };
    }

    private static Predicate buildRulePredicate(CollectionRule rule, Root<Product> root, CriteriaBuilder cb) {
        RuleField field = rule.getField();
        RuleOperator op = rule.getOperator();
        String val = rule.getValue() != null ? rule.getValue().trim() : "";
        String valLower = val.toLowerCase();

        return switch (field) {
            case TITLE -> buildStringPredicate(root.get("title"), op, valLower, cb);
            case TYPE -> buildStringPredicate(root.get("productType"), op, valLower, cb);
            case VENDOR -> buildStringPredicate(root.get("vendor"), op, valLower, cb);
            case TAG -> buildTagPredicate(root.get("tags"), op, valLower, cb);
            case PRICE -> buildPricePredicate(root, op, val, cb);
            case INVENTORY_STOCK -> cb.conjunction(); // Tồn kho sẽ liên kết với phần 5C
        };
    }

    private static Predicate buildStringPredicate(Path<String> path, RuleOperator op, String valLower, CriteriaBuilder cb) {
        var lowerPath = cb.lower(cb.coalesce(path, ""));
        return switch (op) {
            case EQUALS -> cb.equal(lowerPath, valLower);
            case NOT_EQUALS -> cb.notEqual(lowerPath, valLower);
            case CONTAINS -> cb.like(lowerPath, "%" + valLower + "%");
            case NOT_CONTAINS -> cb.notLike(lowerPath, "%" + valLower + "%");
            case STARTS_WITH -> cb.like(lowerPath, valLower + "%");
            case ENDS_WITH -> cb.like(lowerPath, "%" + valLower);
            case GREATER_THAN -> cb.greaterThan(lowerPath, valLower);
            case LESS_THAN -> cb.lessThan(lowerPath, valLower);
        };
    }

    private static Predicate buildTagPredicate(Path<String> path, RuleOperator op, String valLower, CriteriaBuilder cb) {
        var lowerPath = cb.lower(cb.coalesce(path, ""));
        return switch (op) {
            case EQUALS, CONTAINS -> cb.like(lowerPath, "%" + valLower + "%");
            case NOT_EQUALS, NOT_CONTAINS -> cb.notLike(lowerPath, "%" + valLower + "%");
            case STARTS_WITH -> cb.like(lowerPath, "%\"" + valLower + "%");
            case ENDS_WITH -> cb.like(lowerPath, "%" + valLower + "\"%");
            default -> cb.like(lowerPath, "%" + valLower + "%");
        };
    }

    private static Predicate buildPricePredicate(Root<Product> root, RuleOperator op, String val, CriteriaBuilder cb) {
        try {
            BigDecimal targetPrice = new BigDecimal(val);
            Join<Product, ProductVariant> variantJoin = root.join("variants", JoinType.INNER);
            Predicate activeVariant = cb.isNull(variantJoin.get("deletedAt"));
            Path<BigDecimal> pricePath = variantJoin.get("price");

            Predicate pricePred = switch (op) {
                case EQUALS -> cb.equal(pricePath, targetPrice);
                case NOT_EQUALS -> cb.notEqual(pricePath, targetPrice);
                case GREATER_THAN -> cb.greaterThan(pricePath, targetPrice);
                case LESS_THAN -> cb.lessThan(pricePath, targetPrice);
                default -> cb.equal(pricePath, targetPrice);
            };

            return cb.and(activeVariant, pricePred);
        } catch (Exception e) {
            return cb.disjunction();
        }
    }
}
