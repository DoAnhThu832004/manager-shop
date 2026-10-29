package com.thupo.managerShop.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quy tắc điều kiện của bộ sưu tập tự động (Automated Collection).
 */
@Entity
@Table(name = "collection_rules")
@Getter
@Setter
@NoArgsConstructor
public class CollectionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 30)
    private RuleField field;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 30)
    private RuleOperator operator;

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    @Column(name = "is_exclusion", nullable = false)
    private boolean isExclusion = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CollectionRule(Collection collection, RuleField field, RuleOperator operator, String value, boolean isExclusion) {
        this.collection = collection;
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.isExclusion = isExclusion;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
