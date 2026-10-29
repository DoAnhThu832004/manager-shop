package com.thupo.managerShop.supplychain.entity;

import com.thupo.managerShop.auth.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Phiếu nhận hàng chuyển kho (InventoryTransferReceipt) kèm Idempotency Key.
 */
@Entity
@Table(name = "inventory_transfer_receipts")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransferReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private InventoryTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryTransferReceiptItem> receiptItems = new ArrayList<>();

    public InventoryTransferReceipt(InventoryTransfer transfer, User receivedBy, String idempotencyKey, String note) {
        this.transfer = transfer;
        this.receivedBy = receivedBy;
        this.idempotencyKey = idempotencyKey;
        this.note = note;
        this.receivedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
    }
}
