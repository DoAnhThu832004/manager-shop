package com.thupo.managerShop.product.repository;

import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT p FROM Product p WHERE p.deletedAt IS NULL " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:vendor IS NULL OR p.vendor = :vendor) " +
           "AND (:productType IS NULL OR p.productType = :productType) " +
           "AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR p.id IN (SELECT v.product.id FROM ProductVariant v WHERE v.deletedAt IS NULL AND LOWER(v.sku) LIKE LOWER(CONCAT('%', :q, '%'))))")
    Page<Product> searchProducts(@Param("q") String q,
                                 @Param("status") ProductStatus status,
                                 @Param("vendor") String vendor,
                                 @Param("productType") String productType,
                                 Pageable pageable);
}
