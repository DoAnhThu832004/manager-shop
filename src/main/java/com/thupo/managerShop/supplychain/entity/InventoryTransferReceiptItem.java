package com.thupo.managerShop.supplychain.entity;

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
 * Chi tiết mặt hàng nhận được trong phiếu nhận hàng chuyển kho (InventoryTransferReceiptItem).
 */
@Entity
@Table(name = "inventory_transfer_receipt_items")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransferReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InventoryTransferReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryTransferItem item;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public InventoryTransferReceiptItem(InventoryTransferReceipt receipt, InventoryTransferItem item, int quantity) {
        this.receipt = receipt;
        this.item = item;
        this.quantity = quantity;
    }
}
