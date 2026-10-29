package com.thupo.managerShop.supplychain.entity;

import com.thupo.managerShop.product.entity.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dòng hàng trong đơn đặt hàng nhập (PurchaseOrderItem).
 */
@Entity
@Table(name = "purchase_order_items")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "quantity_ordered", nullable = false)
    private int quantityOrdered;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived = 0;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    public PurchaseOrderItem(PurchaseOrder purchaseOrder, ProductVariant variant, int quantityOrdered, BigDecimal unitCost) {
        this.purchaseOrder = purchaseOrder;
        this.variant = variant;
        this.quantityOrdered = quantityOrdered;
        this.quantityReceived = 0;
        this.unitCost = unitCost != null ? unitCost : BigDecimal.ZERO;
    }
}
