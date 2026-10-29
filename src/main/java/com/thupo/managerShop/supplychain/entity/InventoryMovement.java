package com.thupo.managerShop.supplychain.entity;

import com.thupo.managerShop.auth.entity.Branch;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.product.entity.ProductVariant;
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
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thực thể Sổ cái biến động kho (InventoryMovement - Append-Only).
 * Chỉ thêm, không sửa, không xóa.
 */
@Entity
@Table(name = "inventory_movements")
@Getter
@Setter
@NoArgsConstructor
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Branch location;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "available_before", nullable = false)
    private int availableBefore;

    @Column(name = "available_after", nullable = false)
    private int availableAfter;

    @Column(name = "on_hand_before", nullable = false)
    private int onHandBefore;

    @Column(name = "on_hand_after", nullable = false)
    private int onHandAfter;

    @Column(name = "reason")
    private String reason;

    @Column(name = "reference_type", length = 100)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public InventoryMovement(ProductVariant variant,
                             Branch location,
                             MovementType movementType,
                             int quantityDelta,
                             int availableBefore,
                             int availableAfter,
                             int onHandBefore,
                             int onHandAfter,
                             String reason,
                             String referenceType,
                             String referenceId,
                             User createdBy) {
        this.variant = variant;
        this.location = location;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.availableBefore = availableBefore;
        this.availableAfter = availableAfter;
        this.onHandBefore = onHandBefore;
        this.onHandAfter = onHandAfter;
        this.reason = reason;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
