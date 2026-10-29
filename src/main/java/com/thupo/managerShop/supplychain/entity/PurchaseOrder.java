package com.thupo.managerShop.supplychain.entity;

import com.thupo.managerShop.auth.entity.Branch;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thực thể Đơn đặt hàng nhập (Purchase Order).
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", nullable = false, unique = true, length = 50)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id", nullable = false)
    private Branch destinationLocation;

    @Column(name = "reference_number", length = 255)
    private String referenceNumber;

    @Column(name = "note_to_supplier", columnDefinition = "TEXT")
    private String noteToSupplier;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "VND";

    @Column(name = "tags", columnDefinition = "JSON")
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "subtotal_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotalCost = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("receivedAt DESC")
    private List<PurchaseOrderReceipt> receipts = new ArrayList<>();

    public PurchaseOrder(String poNumber, Supplier supplier, Branch destinationLocation) {
        this.poNumber = poNumber;
        this.supplier = supplier;
        this.destinationLocation = destinationLocation;
        this.status = PurchaseOrderStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.currency == null) {
            this.currency = "VND";
        }
        if (this.subtotalCost == null) {
            this.subtotalCost = BigDecimal.ZERO;
        }
        if (this.taxTotal == null) {
            this.taxTotal = BigDecimal.ZERO;
        }
        if (this.totalCost == null) {
            this.totalCost = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
