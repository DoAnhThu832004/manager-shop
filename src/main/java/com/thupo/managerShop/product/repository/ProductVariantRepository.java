package com.thupo.managerShop.product.repository;

import com.thupo.managerShop.product.entity.ProductVariant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    boolean existsBySkuAndDeletedAtIsNull(String sku);

    boolean existsBySkuAndDeletedAtIsNullAndIdNot(String sku, Long id);

    Optional<ProductVariant> findBySkuAndDeletedAtIsNull(String sku);

    List<ProductVariant> findByProductIdAndDeletedAtIsNullOrderByPositionAscIdAsc(Long productId);
}
