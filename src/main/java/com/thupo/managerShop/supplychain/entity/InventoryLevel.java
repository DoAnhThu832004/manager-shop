package com.thupo.managerShop.supplychain.entity;

import com.thupo.managerShop.auth.entity.Branch;
import com.thupo.managerShop.product.entity.ProductVariant;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thực thể Tồn kho theo biến thể và địa điểm (InventoryLevel).
 * Đẳng thức bắt buộc: on_hand = available + committed + unavailable.
 */
@Entity
@Table(name = "inventory_levels", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inv_level_var_loc", columnNames = {"variant_id", "location_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class InventoryLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Branch location;

    @Column(name = "available", nullable = false)
    private int available = 0;

    @Column(name = "on_hand", nullable = false)
    private int onHand = 0;

    @Column(name = "committed", nullable = false)
    private int committed = 0;

    @Column(name = "unavailable", nullable = false)
    private int unavailable = 0;

    @Column(name = "incoming", nullable = false)
    private int incoming = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public InventoryLevel(ProductVariant variant, Branch location) {
        this.variant = variant;
        this.location = location;
        this.available = 0;
        this.onHand = 0;
        this.committed = 0;
        this.unavailable = 0;
        this.incoming = 0;
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
