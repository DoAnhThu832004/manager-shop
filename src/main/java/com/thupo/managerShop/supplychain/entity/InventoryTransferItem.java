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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chi tiết mặt hàng trong đơn chuyển kho (InventoryTransferItem).
 */
@Entity
@Table(name = "inventory_transfer_items")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private InventoryTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "quantity_transferred", nullable = false)
    private int quantityTransferred;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived = 0;

    public InventoryTransferItem(InventoryTransfer transfer, ProductVariant variant, int quantityTransferred) {
        this.transfer = transfer;
        this.variant = variant;
        this.quantityTransferred = quantityTransferred;
        this.quantityReceived = 0;
    }
}
