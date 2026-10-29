package com.thupo.managerShop.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Liên kết giữa Bộ sưu tập thủ công (MANUAL) và Sản phẩm.
 */
@Entity
@Table(name = "collection_products")
@Getter
@Setter
@NoArgsConstructor
public class CollectionProduct {

    @EmbeddedId
    private CollectionProductId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("collectionId")
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("productId")
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "position", nullable = false)
    private int position = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CollectionProduct(Collection collection, Product product, int position) {
        this.collection = collection;
        this.product = product;
        this.position = position;
        this.id = new CollectionProductId(collection.getId(), product.getId());
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.id == null && this.collection != null && this.product != null) {
            this.id = new CollectionProductId(this.collection.getId(), this.product.getId());
        }
    }
}
