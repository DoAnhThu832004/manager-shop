package com.thupo.managerShop.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Biến thể sản phẩm.
 */
@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "title", nullable = false, length = 255)
    private String title = "Default Title";

    @Column(name = "option1", length = 100)
    private String option1;

    @Column(name = "option2", length = 100)
    private String option2;

    @Column(name = "option3", length = 100)
    private String option3;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "compare_at_price", precision = 19, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "cost_per_item", precision = 19, scale = 2)
    private BigDecimal costPerItem;

    @Column(name = "charge_tax", nullable = false)
    private boolean chargeTax = true;

    @Column(name = "unit_price_measure", length = 50)
    private String unitPriceMeasure;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "inventory_tracked", nullable = false)
    private boolean inventoryTracked = true;

    @Column(name = "sell_when_out_of_stock", nullable = false)
    private boolean sellWhenOutOfStock = false;

    @Column(name = "is_physical", nullable = false)
    private boolean isPhysical = true;

    @Column(name = "weight_kg", precision = 10, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "length_cm", precision = 10, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 10, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 10, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "country_of_origin", length = 10)
    private String countryOfOrigin;

    @Column(name = "hs_code", length = 50)
    private String hsCode;

    @Column(name = "position", nullable = false)
    private int position = 1;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.price == null) {
            this.price = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
