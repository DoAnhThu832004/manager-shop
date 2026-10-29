package com.thupo.managerShop.product.repository;

import com.thupo.managerShop.product.entity.CollectionProduct;
import com.thupo.managerShop.product.entity.CollectionProductId;
import com.thupo.managerShop.product.entity.Product;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectionProductRepository extends JpaRepository<CollectionProduct, CollectionProductId> {

    boolean existsById(CollectionProductId id);

    void deleteById(CollectionProductId id);

    void deleteByCollectionId(Long collectionId);

    @Query("SELECT COUNT(cp) FROM CollectionProduct cp " +
           "WHERE cp.collection.id = :collectionId " +
           "AND cp.product.deletedAt IS NULL " +
           "AND cp.product.status = com.thupo.managerShop.product.entity.ProductStatus.ACTIVE")
    long countActiveProductsByCollectionId(@Param("collectionId") Long collectionId);

    @Query("SELECT cp.product FROM CollectionProduct cp " +
           "WHERE cp.collection.id = :collectionId " +
           "AND cp.product.deletedAt IS NULL " +
           "AND cp.product.status = com.thupo.managerShop.product.entity.ProductStatus.ACTIVE " +
           "ORDER BY cp.position ASC")
    Page<Product> findActiveProductsByCollectionId(@Param("collectionId") Long collectionId, Pageable pageable);

    @Query("SELECT MAX(cp.position) FROM CollectionProduct cp WHERE cp.collection.id = :collectionId")
    Integer findMaxPositionByCollectionId(@Param("collectionId") Long collectionId);
}
