package com.thupo.managerShop.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CollectionProductId implements Serializable {

    @Column(name = "collection_id")
    private Long collectionId;

    @Column(name = "product_id")
    private Long productId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollectionProductId that = (CollectionProductId) o;
        return Objects.equals(collectionId, that.collectionId) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionId, productId);
    }
}
